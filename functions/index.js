"use strict";

const { setGlobalOptions } = require("firebase-functions/v2");
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { getMessaging } = require("firebase-admin/messaging");
const crypto = require("node:crypto");
const textToSpeech = require("@google-cloud/text-to-speech");
const voice = require("./voice");
const interpretation = require("./interpretation");

initializeApp();
const speechClient = new textToSpeech.TextToSpeechClient();
setGlobalOptions({
  region: "asia-south1",
  maxInstances: 2,
  memory: "512MiB",
  timeoutSeconds: 300,
});

const TRUECALLER_CLIENT_IDS = Object.freeze({
  user: "flparbrmvudm9zvgtxdonsemkp4qvdepju5b4lwyiqe",
  admin: "soprrd5odvcnyokurr6yw7xsmk74n1ci4n4pvd2amvy",
});
const ADMIN_PHONE = "+919177216132";

function normalizedPhone(value) {
  const raw = typeof value === "string" ? value.trim() : "";
  if (/^\+[1-9][0-9]{7,14}$/.test(raw)) return raw;
  const digits = raw.replace(/\D/g, "");
  if (digits.length === 10) return `+91${digits}`;
  if (digits.length === 12 && digits.startsWith("91")) return `+${digits}`;
  return "";
}

function phoneAssignmentId(phone) {
  return crypto.createHash("sha256").update(phone, "utf8").digest("hex");
}

exports.processTruecallerLogin = onDocumentCreated(
  {
    document: "truecallerRequests/{uid}",
    timeoutSeconds: 30,
    memory: "256MiB",
    maxInstances: 3,
  },
  async (event) => {
    if (!event.data) return;
    const data = event.data.data() || {};
    const role = data.role === "admin" ? "admin" : "user";
    const code = typeof data.code === "string" ? data.code : "";
    const verifier = typeof data.codeVerifier === "string" ? data.codeVerifier : "";
    const state = typeof data.state === "string" ? data.state : "";
    const result = getFirestore().collection("truecallerResults").doc(event.params.uid);
    try {
      if (code.length < 8 || code.length > 4096 || verifier.length < 43
          || verifier.length > 256 || state.length < 16 || state.length > 128) {
        throw new Error("invalid_authorization");
      }
      const tokenResponse = await fetch("https://oauth-account-noneu.truecaller.com/v1/token", {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          client_id: TRUECALLER_CLIENT_IDS[role],
          code,
          code_verifier: verifier,
        }),
      });
      if (!tokenResponse.ok) throw new Error(`token_exchange_${tokenResponse.status}`);
      const token = await tokenResponse.json();
      if (typeof token.access_token !== "string" || token.access_token.length < 16) {
        throw new Error("missing_access_token");
      }
      const profileResponse = await fetch("https://oauth-account-noneu.truecaller.com/v1/userinfo", {
        headers: { authorization: `Bearer ${token.access_token}` },
      });
      if (!profileResponse.ok) throw new Error(`profile_${profileResponse.status}`);
      const profile = await profileResponse.json();
      const phone = normalizedPhone(profile.phone_number);
      if (!phone || profile.phone_number_verified !== true) throw new Error("unverified_phone");
      if (role === "admin" && phone !== ADMIN_PHONE) throw new Error("unauthorized_admin");

      let user;
      try {
        user = await getAuth().getUserByPhoneNumber(phone);
      } catch (error) {
        if (error.code !== "auth/user-not-found") throw error;
        user = await getAuth().updateUser(event.params.uid, { phoneNumber: phone });
      }
      const firebaseToken = await getAuth().createCustomToken(user.uid, {
        login_provider: "truecaller",
      });
      await result.set({ firebaseToken, phone, state, completedAt: FieldValue.serverTimestamp() });
    } catch (error) {
      console.error("Truecaller Firestore login failed", { role, error: error.message });
      await result.set({ error: "truecaller_verification_failed", state,
        completedAt: FieldValue.serverTimestamp() });
    } finally {
      await event.data.ref.delete();
    }
  },
);

exports.syncPhoneAssignment = onDocumentWritten(
  "families/{familyId}/members/{memberId}",
  async (event) => {
    if (!event.data) return;
    const before = event.data.before.exists ? event.data.before.data() : {};
    const after = event.data.after.exists ? event.data.after.data() : {};
    const oldPhone = normalizedPhone(before.phoneE164);
    const newPhone = normalizedPhone(after.phoneE164);
    if (oldPhone === newPhone) return;

    const database = getFirestore();
    const batch = database.batch();
    if (oldPhone) batch.delete(database.collection("phoneAssignments").doc(phoneAssignmentId(oldPhone)));
    if (newPhone) {
      const family = await database.collection("families").doc(event.params.familyId).get();
      batch.set(database.collection("phoneAssignments").doc(phoneAssignmentId(newPhone)), {
        familyId: event.params.familyId,
        memberId: event.params.memberId,
        phoneE164: newPhone,
        ownerUid: family.exists ? family.get("ownerUid") || "" : "",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    await batch.commit();
  },
);

async function synthesizeClip(reference, text, language) {
  const code = voice.language(language);
  const [response] = await speechClient.synthesizeSpeech({
    input: voice.synthesisInput(text, code),
    voice: { languageCode: voice.languageCode(code), name: voice.voiceName(code) },
    audioConfig: { audioEncoding: "MP3" },
  });
  const rawAudio = response.audioContent;
  const audio = typeof rawAudio === "string"
    ? Buffer.from(rawAudio, "base64")
    : Buffer.from(rawAudio || []);
  if (!voice.validMp3Buffer(audio)) {
    throw new Error(`Synthesized response is not a valid MP3 (${audio.length} bytes)`);
  }
  await reference.set({
    text,
    voice: voice.voiceName(code),
    language: code,
    mimeType: "audio/mpeg",
    audioBase64: audio.toString("base64"),
    generatedAt: FieldValue.serverTimestamp(),
    generatorVersion: 1,
  });
}

async function deleteClips(documents) {
  for (let offset = 0; offset < documents.length; offset += 450) {
    const batch = getFirestore().batch();
    for (const document of documents.slice(offset, offset + 450)) batch.delete(document.ref);
    await batch.commit();
  }
}

function memberVoiceSignature(member) {
  if (!member) return "deleted";
  return JSON.stringify(voice.voiceEntries(member)
    .map((entry) => `${entry.language}\n${entry.text}`).sort());
}

async function syncNaturalVoiceSnapshot(memberReference, member) {
  const collection = memberReference.collection("voiceClips");
  const existing = await collection.get();
  if (!member) {
    await deleteClips(existing.docs);
    return;
  }

  const desired = new Map(voice.voiceEntries(member)
    .map((entry) => [voice.clipId(entry.text, entry.language), entry]));
  const missing = [];
  const stale = [];
  for (const document of existing.docs) {
    const expected = desired.get(document.id);
    const value = document.data();
    if (!expected) {
      stale.push(document);
    } else if (!voice.validClipDocument(value, expected.text, expected.language)) {
      missing.push([document.id, expected]);
    }
    desired.delete(document.id);
  }
  for (const item of desired) missing.push(item);

  // Keep concurrency small so one family update cannot overwhelm the TTS quota.
  for (let offset = 0; offset < missing.length; offset += 3) {
    await Promise.all(missing.slice(offset, offset + 3)
      .map(([id, entry]) => synthesizeClip(collection.doc(id), entry.text, entry.language)));
  }
  await deleteClips(stale);
  console.log("Natural voice clips ready", {
    memberId: memberReference.id,
    languages: [...new Set(voice.voiceEntries(member).map((entry) => entry.language))],
    generated: missing.length,
    reused: existing.size - stale.length,
    removed: stale.length,
  });
}

async function syncNaturalVoiceClips(memberReference, eventMember) {
  let target = eventMember;
  // Firestore may run rapid member updates concurrently. Re-read and converge on
  // the newest document so a slower, older event cannot leave stale voice clips.
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await syncNaturalVoiceSnapshot(memberReference, target);
    const currentSnapshot = await memberReference.get();
    const current = currentSnapshot.exists ? currentSnapshot.data() : null;
    if (memberVoiceSignature(current) === memberVoiceSignature(target)) return;
    target = current;
  }
  throw new Error("Member kept changing while natural voice clips were generated");
}

exports.notifyScheduleChanged = onDocumentWritten(
  {
    document: "families/{familyId}/members/{memberId}",
    retry: true,
  },
  async (event) => {
    const { familyId, memberId } = event.params;
    const after = event.data && event.data.after.exists ? event.data.after.data() : null;

    // Finish the natural Telugu clips before telling phones to sync. The phone can
    // then download the new schedule and every required clip in one pass, activate
    // them atomically, and use the complete conversation without internet.
    await syncNaturalVoiceClips(event.data.after.ref, after);

    const devices = await getFirestore()
      .collection("families")
      .doc(familyId)
      .collection("devices")
      .where("memberId", "==", memberId)
      .get();

    const tokens = [...new Set(devices.docs
      .map((document) => document.get("fcmToken"))
      .filter((token) => typeof token === "string" && token.length > 20))];

    if (tokens.length === 0) {
      console.log("No paired phones for member", memberId);
    } else {
      const revision = after && after.revision ? String(after.revision) : "deleted";
      for (let offset = 0; offset < tokens.length; offset += 500) {
        const batch = tokens.slice(offset, offset + 500);
        const result = await getMessaging().sendEachForMulticast({
          tokens: batch,
          data: {
            type: "schedule_updated",
            familyId,
            memberId,
            revision,
          },
          android: {
            priority: "high",
            // Keep only the newest collapsed revision while the phone is offline,
            // and deliver it when connectivity returns. FCM's Android maximum is 28 days.
            ttl: 28 * 24 * 60 * 60 * 1000,
            collapseKey: `schedule-${memberId}`,
          },
        });
        console.log("Schedule push complete", {
          memberId,
          success: result.successCount,
          failed: result.failureCount,
        });
      }
    }

  },
);

exports.notifyDeviceAssigned = onDocumentWritten(
  "families/{familyId}/devices/{deviceId}",
  async (event) => {
    if (!event.data) return;
    const before = event.data.before.exists ? event.data.before.data() : {};
    if (!event.data.after.exists) {
      const token = typeof before.fcmToken === "string" ? before.fcmToken : "";
      if (token.length < 20) return;
      await getMessaging().send({
        token,
        data: {
          type: "device_deleted",
          familyId: event.params.familyId,
          memberId: typeof before.memberId === "string" ? before.memberId : "",
        },
        android: {
          priority: "high",
          ttl: 28 * 24 * 60 * 60 * 1000,
        },
      });
      console.log("Device removal push sent", { deviceId: event.params.deviceId });
      return;
    }
    const after = event.data.after.data();
    const memberId = typeof after.memberId === "string" ? after.memberId : "";
    if (!memberId || memberId === before.memberId) return;
    if (typeof after.fcmToken !== "string" || after.fcmToken.length < 20) return;

    await getMessaging().send({
      token: after.fcmToken,
      data: {
        type: "device_assigned",
        familyId: event.params.familyId,
        memberId,
      },
      android: {
        priority: "high",
        ttl: 28 * 24 * 60 * 60 * 1000,
      },
    });
    console.log("Assignment push sent", { deviceId: event.params.deviceId, memberId });
  },
);

exports.processTeluguReply = onDocumentCreated(
  {
    document: "families/{familyId}/devices/{deviceId}/speechRequests/{requestId}",
    timeoutSeconds: 30,
    memory: "1GiB",
    maxInstances: 3,
  },
  async (event) => {
    if (!event.data) return;
    const data = event.data.data() || {};
    const { familyId, deviceId, requestId } = event.params;
    const memberId = typeof data.memberId === "string" ? data.memberId : "";
    const phase = data.phase === "meal" ? "meal" : data.phase === "medicine" ? "medicine" : "";
    const device = await getFirestore().collection("families").doc(familyId)
      .collection("devices").doc(deviceId).get();
    if (!device.exists || device.get("memberId") !== memberId
        || device.get("status") !== "assigned" || !phase) {
      await event.data.ref.delete();
      return;
    }

    const context = {
      phase,
      waiting: data.waiting === true,
      duringPrompt: data.duringPrompt === true,
      promptText: typeof data.promptText === "string" ? data.promptText.slice(0, 300) : "",
    };
    let result;
    if (data.textOnly === true) {
      result = await interpretation.interpretText(data.transcript, context);
    } else {
      let pcm;
      try {
        pcm = interpretation.decodeAudioBase64(data.audioBase64);
      } catch (error) {
        await event.data.ref.delete();
        return;
      }
      const interpreter = data.fast === true
        ? interpretation.interpretFast : interpretation.interpret;
      result = await interpreter(pcm, context);
    }
    const resultReference = event.data.ref.parent.parent
      .collection("speechResults").doc(requestId);
    let delivered = false;
    await getFirestore().runTransaction(async (transaction) => {
      // The phone may have timed out and deleted this request while the model was
      // working. The transaction prevents a late, orphaned result from being left.
      const pending = await transaction.get(event.data.ref);
      if (!pending.exists) return;
      transaction.set(resultReference, {
        intent: result.intent,
        heard: result.heard,
        confidence: result.confidence,
        delayMinutes: result.delayMinutes || 0,
        source: result.source,
        completedAt: FieldValue.serverTimestamp(),
      });
      // The parent's audio exists only while this event is being processed.
      transaction.delete(event.data.ref);
      delivered = true;
    });
    console.log("Telugu reply interpreted", {
      deviceId,
      phase,
      intent: result.intent,
      source: result.source,
      confidence: result.confidence,
      delivered,
      // Deliberately do not log or persist audio or the parent's words.
    });
  },
);
