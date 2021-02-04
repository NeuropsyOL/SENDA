package com.example.aliayubkhan.senda;


import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.AndroidJUnit4;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SENDA_start_all {

    @Rule
    public ActivityTestRule<SplashScreenActivity> mActivityTestRule = new ActivityTestRule<>(SplashScreenActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule =
            GrantPermissionRule.grant(
                    "android.permission.RECORD_AUDIO");

    @Test
    public void sENDA_start_all() {
        DataInteraction checkedTextView = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(0);
        checkedTextView.perform(click());

        DataInteraction checkedTextView2 = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(1);
        checkedTextView2.perform(click());


        DataInteraction checkedTextView4 = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(2);
        checkedTextView4.perform(click());

        DataInteraction checkedTextView5 = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(3);
        checkedTextView5.perform(click());

        DataInteraction checkedTextView6 = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(4);
        checkedTextView6.perform(click());

        DataInteraction checkedTextView7 = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(5);
        checkedTextView7.perform(click());

        DataInteraction checkedTextView8 = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(6);
        checkedTextView8.perform(click());

        DataInteraction checkedTextView9 = onData(anything())
                .inAdapterView(allOf(withId(R.id.sensors),
                        childAtPosition(
                                withId(R.id.linearVertical),
                                0)))
                .atPosition(7);
        checkedTextView9.perform(click());

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

        //TODO add countdown here
        // https://stackoverflow.com/questions/30155227/espresso-how-to-wait-for-some-time1-hour
        // or https://github.com/chiuki/espresso-samples/tree/master/idling-resource-elapsed-time

        //TODO also, start Recorder here, but maybe not from inside here, but instead follow:
        // https://developer.android.com/training/testing/ui-testing/uiautomator-testing


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
