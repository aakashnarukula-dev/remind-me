#!/usr/bin/env python3
"""Generate prerecorded Telugu call branches and the looping Chitti ringtone."""

import asyncio
import math
import struct
import wave
from pathlib import Path

import edge_tts


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "parent" / "src" / "main" / "res" / "raw"
VOICE = "te-IN-ShrutiNeural"

RELATIONS = {"nanna": "నాన్నా", "amma": "అమ్మా", "generic": "అండి"}
MEDICINES = {
    "generic": "టాబ్లెట్",
    "cholesterol": "కొలెస్ట్రాల్ టాబ్లెట్",
    "bp": "బీపీ టాబ్లెట్",
    "sugar": "షుగర్ టాబ్లెట్",
    "thyroid": "థైరాయిడ్ టాబ్లెట్",
}


def call_scripts():
    items = {}
    for key, address in RELATIONS.items():
        items[f"pre_question_{key}"] = (
            f"హలో {address}! భోజనం చేశారా?"
        )
        items[f"pre_yes_{key}"] = (
            "అయితే ఇంకో ముప్పై నిమిషాల్లో మీరు టాబ్లెట్ వేసుకోవాలి. "
            "నేను ఇంకో ముప్పై నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. అప్పుడు వేసుకోండి. బై!"
        )
        items[f"med_no_{key}"] = (
            "అయితే త్వరగా వెళ్లి టాబ్లెట్ వేసుకోండి. నేను లైన్‌లోనే ఉంటాను. "
            "వేసుకుని వచ్చాక, ‘వేసుకున్నా’ బటన్ నొక్కండి."
        )
        items[f"med_yes_{key}"] = (
            f"సూపర్ {address}! ఉంటాను, బై."
        )
        for medicine_key, medicine in MEDICINES.items():
            items[f"med_{medicine_key}_{key}"] = (
                f"హలో {address}! {medicine} వేసుకున్నారా?"
            )
    items["pre_no"] = (
        "అయితే త్వరగా వెళ్లి భోజనం చేయండి. ఇంకో ముప్పై నిమిషాల్లో మీరు టాబ్లెట్ వేసుకోవాలి."
    )
    items["pre_acknowledged"] = (
        "సరే. నేను ఇంకో ముప్పై నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. అప్పుడు వేసుకోండి. బై!"
    )
    return items


async def generate_one(name, script, semaphore):
    async with semaphore:
        target = OUT / f"{name}.mp3"
        await edge_tts.Communicate(script, VOICE, rate="-4%").save(str(target))


async def generate_speech():
    OUT.mkdir(parents=True, exist_ok=True)
    semaphore = asyncio.Semaphore(4)
    await asyncio.gather(*(generate_one(name, script, semaphore) for name, script in call_scripts().items()))


def generate_ring():
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / "incoming_ring.wav"
    sample_rate = 22050
    notes = [(0.0, 0.55, 659.25), (0.62, 1.1, 783.99), (1.18, 1.75, 987.77)]
    frames = []
    for index in range(int(sample_rate * 4.0)):
        time = index / sample_rate
        value = 0.0
        for start, end, frequency in notes:
            if start <= time <= end:
                local = time - start
                length = end - start
                envelope = max(0.0, min(1.0, local / 0.04, (length - local) / 0.12))
                value += math.sin(2 * math.pi * frequency * time) * envelope * 0.22
                value += math.sin(4 * math.pi * frequency * time) * envelope * 0.035
        frames.append(struct.pack("<h", int(max(-1, min(1, value)) * 32767)))
    with wave.open(str(target), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        output.writeframes(b"".join(frames))


if __name__ == "__main__":
    asyncio.run(generate_speech())
    generate_ring()
