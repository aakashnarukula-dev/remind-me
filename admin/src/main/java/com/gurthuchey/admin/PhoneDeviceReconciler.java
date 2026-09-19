package com.gurthuchey.admin;

import java.util.List;
import java.util.Set;

/** Identifies abandoned Firebase identities without hiding unrelated parent phones. */
final class PhoneDeviceReconciler {
    private PhoneDeviceReconciler() {}

    static boolean isSuperseded(AdminFirebase.PhoneDevice candidate,
                                List<AdminFirebase.PhoneDevice> devices,
                                Set<String> activeMemberIds) {
        for (AdminFirebase.PhoneDevice other : devices) {
            if (candidate == other || candidate.uid.equals(other.uid)
                    || !isPreferredReplacement(candidate, other, activeMemberIds)) continue;
            boolean stableIdentityMatches = !candidate.deviceKey.isEmpty()
                    && candidate.deviceKey.equals(other.deviceKey);
            boolean sameModelReplacement = candidate.model.equalsIgnoreCase(other.model)
                    && (candidate.memberId.equals(other.memberId)
                        || (!activeMemberIds.contains(candidate.memberId)
                            && activeMemberIds.contains(other.memberId)));
            if (stableIdentityMatches || sameModelReplacement) return true;
        }
        return false;
    }

    private static boolean isPreferredReplacement(AdminFirebase.PhoneDevice candidate,
                                                  AdminFirebase.PhoneDevice other,
                                                  Set<String> activeMemberIds) {
        if (other.updatedAt != candidate.updatedAt) return other.updatedAt > candidate.updatedAt;
        boolean candidateActive = activeMemberIds.contains(candidate.memberId);
        boolean otherActive = activeMemberIds.contains(other.memberId);
        if (candidateActive != otherActive) return otherActive;
        return other.uid.compareTo(candidate.uid) > 0;
    }

    static AdminFirebase.PhoneDevice connectedPhone(String memberId,
                                                     List<AdminFirebase.PhoneDevice> devices,
                                                     Set<String> activeMemberIds) {
        AdminFirebase.PhoneDevice newest = null;
        for (AdminFirebase.PhoneDevice phone : devices) {
            if (!memberId.equals(phone.memberId) || phone.isWaiting()
                    || isSuperseded(phone, devices, activeMemberIds)) continue;
            if (newest == null || phone.updatedAt > newest.updatedAt) newest = phone;
        }
        return newest;
    }
}
