package com.example.aliayubkhan.senda;


import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.IdlingPolicies;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.assertThat;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class SENDA_start_all {

    @Rule
    public ActivityTestRule<SplashScreenActivity> mActivityTestRule = new ActivityTestRule<>(SplashScreenActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule =
            GrantPermissionRule.grant(
                    "android.permission.RECORD_AUDIO");

    // define test duration and make sure that no timeout occurs
    // alternatives: long waitingTime = DateUtils.SECOND_IN_MILLIS * 5; //MINUTE_IN_MILLIS; // DateUtils.HOUR_IN_MILLIS;
//    private static long waitingTime = DateUtils.SECOND_IN_MILLIS * 2;

    @Test
    public void sENDA_select_all() {
        // get the number of view items in this test class using a matcher instead
        // this number is the same for all devices right now and this is the laziest solution.
        int num_sensors = MainActivity.SENSOR_COUNT;

        // check all items in the list view of sensors
        for (int items = 0; items< num_sensors; items++){
            DataInteraction checkedTextView = onData(anything())
                    .inAdapterView(allOf(withId(R.id.sensors),
                            childAtPosition(
                                    withId(R.id.linearVertical),
                                    0)))
                    .atPosition(items);
            checkedTextView.perform(click());
        }
    }

    // from https://stackoverflow.com/questions/30155227/espresso-how-to-wait-for-some-time1-hour
    @Test
    public void start_and_wait_some_time() throws InterruptedException {

        System.out.println("begin start_and_wait_some_time");

        int num_sensors = MainActivity.SENSOR_COUNT;

        // check all items in the list view of sensors
        for (int items = 0; items< num_sensors; items++){
            DataInteraction checkedTextView = onData(anything())
                    .inAdapterView(allOf(withId(R.id.sensors),
                            childAtPosition(
                                    withId(R.id.linearVertical),
                                    0)))
                    .atPosition(items);
            checkedTextView.perform(click());
        }

        // start lsl streams
        ViewInteraction button = onView(
                allOf(withId(R.id.startLSL), withText("START LSL"),
                        childAtPosition(
                                allOf(withId(R.id.linearHorizontal),
                                        childAtPosition(
                                                withId(R.id.activity_lsldemo),
                                                2)),
                                0),
                        isDisplayed()));
        button.perform(click());
        System.out.println("Start Button was cklicked");

//        // Make sure Espresso does not time out
//        // Make sure Espresso does not time out
//        IdlingPolicies.setMasterPolicyTimeout(2, TimeUnit.SECONDS);
//        IdlingPolicies.setIdlingResourceTimeout(2, TimeUnit.SECONDS);
////
////        // Now we wait
//        IdlingResource idlingResource = new ElapsedTimeIdlingResource(2);
//        IdlingRegistry.getInstance().register(idlingResource);

        Thread.sleep(DateUtils.SECOND_IN_MILLIS*2);

        // after the time has passed, stop the streams
        ViewInteraction button2 = onView(
                allOf(withId(R.id.stopLSL), withText("STOP LSL"),
                        childAtPosition(
                                allOf(withId(R.id.linearHorizontal),
                                        childAtPosition(
                                                withId(R.id.activity_lsldemo),
                                                2)),
                                1),
                        isDisplayed()));
        button2.perform(click());
        System.out.println("Stop button was clicked");
//
//        // Clean up
//        IdlingRegistry.getInstance().unregister(idlingResource);
    }


    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }

}
