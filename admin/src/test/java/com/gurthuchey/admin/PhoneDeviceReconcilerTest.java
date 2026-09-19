package com.gurthuchey.admin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class PhoneDeviceReconcilerTest {
    @Test public void oldIdentityForRemovedMemberIsReplacedByNewerMatchingPhone() {
        AdminFirebase.PhoneDevice old = phone("old", "samsung SM-S908E", "removed", "", 10);
        AdminFirebase.PhoneDevice current = phone("new", "samsung SM-S908E", "aakash", "", 20);

        assertTrue(PhoneDeviceReconciler.isSuperseded(old, Arrays.asList(old, current),
                Collections.singleton("aakash")));
        assertFalse(PhoneDeviceReconciler.isSuperseded(current, Arrays.asList(old, current),
                Collections.singleton("aakash")));
        assertTrue(PhoneDeviceReconciler.connectedPhone("aakash", Arrays.asList(old, current),
                Collections.singleton("aakash")) == current);
    }

    @Test public void olderDuplicateForSamePersonIsRemoved() {
        AdminFirebase.PhoneDevice old = phone("old", "Samsung A1", "aakash", "", 10);
        AdminFirebase.PhoneDevice current = phone("new", "Samsung A1", "aakash", "", 20);
        assertTrue(PhoneDeviceReconciler.isSuperseded(old, Arrays.asList(old, current),
                Collections.singleton("aakash")));
    }

    @Test public void identicalModelsForDifferentActivePeopleAreKept() {
        AdminFirebase.PhoneDevice father = phone("father", "Samsung A1", "nanna", "", 10);
        AdminFirebase.PhoneDevice mother = phone("mother", "Samsung A1", "amma", "", 20);
        assertFalse(PhoneDeviceReconciler.isSuperseded(father, Arrays.asList(father, mother),
                new java.util.HashSet<>(Arrays.asList("nanna", "amma"))));
    }

    @Test public void stableDeviceKeySurvivesModelTextChanges() {
        AdminFirebase.PhoneDevice old = phone("old", "Samsung old label", "aakash", "stable-key", 10);
        AdminFirebase.PhoneDevice current = phone("new", "Samsung new label", "aakash", "stable-key", 20);
        assertTrue(PhoneDeviceReconciler.isSuperseded(old, Arrays.asList(old, current),
                Collections.singleton("aakash")));
    }

    @Test public void equalTimestampsNeverDeleteBothDuplicates() {
        AdminFirebase.PhoneDevice first = phone("a", "Samsung A1", "aakash", "stable", 20);
        AdminFirebase.PhoneDevice second = phone("b", "Samsung A1", "aakash", "stable", 20);
        java.util.List<AdminFirebase.PhoneDevice> devices = Arrays.asList(first, second);
        boolean firstRemoved = PhoneDeviceReconciler.isSuperseded(first, devices, Collections.singleton("aakash"));
        boolean secondRemoved = PhoneDeviceReconciler.isSuperseded(second, devices, Collections.singleton("aakash"));
        assertTrue(firstRemoved ^ secondRemoved);
    }

    private static AdminFirebase.PhoneDevice phone(String uid, String model, String member,
                                                     String key, long updatedAt) {
        AdminFirebase.PhoneDevice value = new AdminFirebase.PhoneDevice();
        value.uid = uid;
        value.model = model;
        value.memberId = member;
        value.status = "assigned";
        value.appVersion = "test";
        value.deviceKey = key;
        value.updatedAt = updatedAt;
        return value;
    }
}
