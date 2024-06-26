/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.services.telephony;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.os.Looper;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.PhoneAccount;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tests the functionality in TelephonyConferenceController.java
 * Assumption: these tests are based on setting status manually
 */

public class TelephonyConferenceControllerTest {

    @Mock
    private TelephonyConnectionServiceProxy mMockTelephonyConnectionServiceProxy;

    @Mock
    private TelephonyConferenceBase.TelephonyConferenceListener mMockListener;

    private TestTelephonyConnection mTestTelephonyConnectionA;
    private TestTelephonyConnection mTestTelephonyConnectionB;

    private TelephonyConferenceController mControllerTest;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mTestTelephonyConnectionA = new TestTelephonyConnection();
        mTestTelephonyConnectionB = new TestTelephonyConnection();

        mControllerTest = new TelephonyConferenceController(mMockTelephonyConnectionServiceProxy);
    }

    /**
     * Behavior: add telephony connections B and A to conference controller,
     *           set status for connections and calls, remove one call
     * Assumption: after performing the behaviours, the status of Connection A is STATE_ACTIVE;
     *             the status of Connection B is STATE_HOLDING;
     *             the call in the original connection is Call.State.ACTIVE;
     *             isMultiparty of the call is false;
     *             isConferenceSupported of the connection is True
     * Expected: Connection A and Connection B are conferenceable with each other
     */
    @Test
    @SmallTest
    public void testConferenceable() {

        when(mTestTelephonyConnectionA.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(false);
        when(mTestTelephonyConnectionB.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(false);

        // add telephony connection B
        mControllerTest.add(mTestTelephonyConnectionB);

        // add telephony connection A
        mControllerTest.add(mTestTelephonyConnectionA);

        mTestTelephonyConnectionA.setTelephonyConnectionActive();
        mTestTelephonyConnectionB.setTelephonyConnectionOnHold();

        assertTrue(mTestTelephonyConnectionA.getConferenceables()
                .contains(mTestTelephonyConnectionB));
        assertTrue(mTestTelephonyConnectionB.getConferenceables()
                .contains(mTestTelephonyConnectionA));

        // verify addConference method is never called
        verify(mMockTelephonyConnectionServiceProxy, never())
                .addConference(any(TelephonyConference.class));

        // call A removed
        mControllerTest.remove(mTestTelephonyConnectionA);
        assertFalse(mTestTelephonyConnectionB.getConferenceables()
                .contains(mTestTelephonyConnectionA));
    }

    /**
     * Verify the connection with "Conference Call" with PROPERTY_IS_DOWNGRADED_CONFERENCE
     * during SRVCC
     */
    @Test
    @SmallTest
    public void testSrvccConferenceConnection() {
        when(mTestTelephonyConnectionA.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(true);
        when(mTestTelephonyConnectionB.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(true);

        List<Connection> listConnections = Arrays.asList(
                mTestTelephonyConnectionA, mTestTelephonyConnectionB);
        when(mMockTelephonyConnectionServiceProxy.getAllConnections()).thenReturn(listConnections);

        mTestTelephonyConnectionA.setAddress(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, "Conference Call", null), 0);
        mTestTelephonyConnectionA.setTelephonyConnectionProperties(
                Connection.PROPERTY_IS_DOWNGRADED_CONFERENCE);
        mTestTelephonyConnectionB.setAddress(
                Uri.fromParts(PhoneAccount.SCHEME_TEL, "5551213", null), 0);
        mTestTelephonyConnectionB.setTelephonyConnectionProperties(
                Connection.PROPERTY_IS_DOWNGRADED_CONFERENCE);

        // add telephony connection B
        mControllerTest.add(mTestTelephonyConnectionB);

        // add telephony connection A
        mControllerTest.add(mTestTelephonyConnectionA);

        // verify the connection with "Conference Call" has PROPERTY_IS_DOWNGRADED_CONFERENCE
        assertTrue((mTestTelephonyConnectionA.getConnectionProperties()
                & Connection.PROPERTY_IS_DOWNGRADED_CONFERENCE) != 0);

        // verify the connection with "5551213" hasn't PROPERTY_IS_DOWNGRADED_CONFERENCE
        assertFalse((mTestTelephonyConnectionB.getConnectionProperties()
                & Connection.PROPERTY_IS_DOWNGRADED_CONFERENCE) != 0);
    }

    /**
     * Behavior: add telephony connection B and A to conference controller,
     *           set status for connections and merged calls, remove one call
     * Assumption: after performing the behaviours, the status of Connection A is STATE_ACTIVE;
     *             the status of Connection B is STATE_HOLDING;
     *             the call in the original connection is Call.State.ACTIVE;
     *             isMultiparty of the call is True;
     *             isConferenceSupported of the connection is True
     * Expected: Connection A and Connection B are conferenceable with each other
     *           addConference is called
     */
    @Test
    @SmallTest
    public void testMergeMultiPartyCalls() {

        // set isMultiparty() true to create the same senario of merge behaviour
        when(mTestTelephonyConnectionA.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(true);
        when(mTestTelephonyConnectionB.mMockRadioConnection.getCall()
                .isMultiparty()).thenReturn(true);

        // Add connections into connection Service
        Collection<Connection> allConnections = new ArrayList<Connection>();
        allConnections.add(mTestTelephonyConnectionA);
        allConnections.add(mTestTelephonyConnectionB);
        when(mMockTelephonyConnectionServiceProxy.getAllConnections())
                .thenReturn(allConnections);

        // add telephony connection B
        mControllerTest.add(mTestTelephonyConnectionB);

        // add telephony connection A
        mControllerTest.add(mTestTelephonyConnectionA);

        mTestTelephonyConnectionA.setTelephonyConnectionActive();
        mTestTelephonyConnectionB.setTelephonyConnectionOnHold();

        assertTrue(mTestTelephonyConnectionA.getConferenceables()
                .contains(mTestTelephonyConnectionB));
        assertTrue(mTestTelephonyConnectionB.getConferenceables()
                .contains(mTestTelephonyConnectionA));

        // capture the argument in the addConference method, and verify it is called
        ArgumentCaptor<TelephonyConference> argumentCaptor = ArgumentCaptor.
                forClass(TelephonyConference.class);
        verify(mMockTelephonyConnectionServiceProxy).addConference(argumentCaptor.capture());

        // add a listener to the added conference
        argumentCaptor.getValue().addTelephonyConferenceListener(mMockListener);

        verify(mMockListener, never()).onDestroyed(any(Conference.class));

        // call A removed
        mControllerTest.remove(mTestTelephonyConnectionA);
        assertFalse(mTestTelephonyConnectionB.getConferenceables()
                .contains(mTestTelephonyConnectionA));

        //onDestroy should be called during the destroy
        verify(mMockListener).onDestroyed(any(Conference.class));
    }
}
