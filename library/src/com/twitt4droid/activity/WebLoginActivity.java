/*
 * Copyright 2014 Daniel Pedraza-Arcega
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
 * limitations under the License.
 */
package com.twitt4droid.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.twitt4droid.R;
import com.twitt4droid.Resources;
import com.twitt4droid.Twitt4droid;

import twitter4j.AsyncTwitter;
import twitter4j.TwitterAdapter;
import twitter4j.TwitterException;
import twitter4j.TwitterMethod;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

/**
 * This Activity provides the web based Twitter login process. Is not meant to 
 * be used directly (DO NOT START IT DIRECTLY). Add this activity to your 
 * AndroidManifest.xml like this:
 * <pre>
 * {@code 
 * <activity android:name="com.twitt4droid.activity.WebLoginActivity"
 *           android:theme="@android:style/Theme.Black.NoTitleBar" />
 * }
 * </pre>
 * 
 * @author Daniel Pedraza-Arcega
 * @since version 1.0
 */
public class WebLoginActivity extends Activity {

    /** The request code for this activity. */
    public static final int REQUEST_CODE = 340;

    /** The name of the Intent-extra used to indicate the twitter user returned. */
    public static final String EXTRA_USER = "com.twitt4droid.extra.user";
     
    private static final String TAG = WebLoginActivity.class.getSimpleName();
    private static final String OAUTH_VERIFIER_CALLBACK_PARAMETER = "oauth_verifier";
    private static final String DENIED_CALLBACK_PARAMETER = "denied";
    private static final String CALLBACK_URL = "oauth://twitt4droid";

    private AsyncTwitter twitter;
    private TextView urlTextView;
    private ProgressBar loadingBar;
    private ImageButton refreshCancelButton;
    private WebView webView;

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Twitt4droid.areConsumerTokensAvailable(getApplicationContext())) {
            if (Resources.isConnectedToInternet(this)) {
                setUpTwitter();
                if (Twitt4droid.isUserLoggedIn(this)) twitter.verifyCredentials();
                else {
                    setContentView(R.layout.twitt4droid_web_browser);
                    setUpView();
                    twitter.getOAuthRequestTokenAsync(CALLBACK_URL);
                }
            } else {
                Log.w(TAG, "No Internet connection detected");
                showNetworkAlertDialog();
            }
        } else {
            Log.e(TAG, "Twitter consumer key and/or consumer secret are not defined correctly");
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    /** Sets up Twitter async listeners. */
    private void setUpTwitter() { 
        twitter = Twitt4droid.getAsyncTwitter(this);
        twitter.addListener(new TwitterAdapter() {
            @Override
            public void verifiedCredentials(final User user) {
                runOnUiThread(new Runnable() {
                    
                    @Override
                    public void run() {
                        handleUserValidation(user);
                    }
                });
            }

            @Override
            public void gotOAuthRequestToken(final RequestToken token) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        String url = token.getAuthenticationURL();
                        urlTextView.setText(url);
                        webView.loadUrl(url);
                    }
                });
            }

            @Override
            public void gotOAuthAccessToken(final AccessToken token) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Twitt4droid.saveAuthenticationInfo(getApplicationContext(), token);
                    }
                });
            }

            @Override
            public void onException(TwitterException te, TwitterMethod method) {
                Log.e(TAG, "Twitter error in " + method, te);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showErrorAlertDialog();
                    }
                });
            }
        });
    }

    /** Shows a network error alert dialog. */
    private void showNetworkAlertDialog() {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.twitt4droid_is_offline_title)
            .setMessage(R.string.twitt4droid_is_offline_messege)
            .setNegativeButton(android.R.string.cancel, 
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i(TAG, "User canceled authentication process due to network failure");
                        setResult(RESULT_CANCELED, getIntent());
                        finish();
                    }
            })
            .setPositiveButton(R.string.twitt4droid_goto_settings, 
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                        finish();
                    }
                })
            .setCancelable(false)
            .show();
    }

    /** Sets up views. */
    @SuppressWarnings("deprecation")
    private void setUpView() {
        urlTextView = (TextView) findViewById(R.id.url_text);
        loadingBar = (ProgressBar) findViewById(R.id.loading_bar);
        refreshCancelButton = (ImageButton) findViewById(R.id.refresh_cancel_botton);
        webView = (WebView) findViewById(R.id.web_view);
        refreshCancelButton.setOnClickListener(new View.OnClickListener() {
            
            @Override
            public void onClick(View v) {
                onRefreshCancelButtonClick();
            }
        });
        CookieSyncManager.createInstance(this);
        CookieManager.getInstance().removeAllCookie();
        webView.getSettings().setSaveFormData(false);
        webView.getSettings().setSavePassword(false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress == 100) {
                    loadingBar.setVisibility(View.INVISIBLE);
                    loadingBar.setProgress(0);
                    refreshCancelButton.setContentDescription(getString(R.string.twitt4droid_refresh_button_title));
                    refreshCancelButton.setImageResource(R.drawable.twitt4droid_ic_refresh_holo_dark);
                } else loadingBar.setVisibility(View.VISIBLE);
                loadingBar.setProgress(progress);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                urlTextView.setText(url);
                if (url.startsWith(CALLBACK_URL)) {
                    Uri uri = Uri.parse(url);
                    if (uri.getQueryParameter(DENIED_CALLBACK_PARAMETER) != null) {
                        setResult(RESULT_CANCELED, getIntent());
                        finish();
                        return true;
                    }
                    if (uri.getQueryParameter(OAUTH_VERIFIER_CALLBACK_PARAMETER) != null) {
                        String oauthVerifier = uri.getQueryParameter(OAUTH_VERIFIER_CALLBACK_PARAMETER);
                        twitter.getOAuthAccessTokenAsync(oauthVerifier);
                        twitter.verifyCredentials();
                        return true;
                    }
                }

                return super.shouldOverrideUrlLoading(view, url);
            }
        });
    }

    /**
     * Handles user validation.
     * 
     * @param user the validated user.
     */
    private void handleUserValidation(User user) {
        Twitt4droid.saveOrUpdateUser(user, getApplicationContext());
        Intent data = getIntent();
        data.putExtra(EXTRA_USER, user);
        setResult(RESULT_OK, data);
        finish();
    }

    /** Shows a generic alert dialog. */
    private void showErrorAlertDialog() {
        new AlertDialog.Builder(WebLoginActivity.this)
            .setTitle(R.string.twitt4droid_error_title)
            .setMessage(R.string.twitt4droid_error_message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setNegativeButton(R.string.twitt4droid_return,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(RESULT_CANCELED, getIntent());
                        finish();
                    }
                })
            .setPositiveButton(R.string.twitt4droid_continue, null)
            .setCancelable(false)
            .show();
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            urlTextView.setText(webView.getUrl());
        } else {
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    /** Action when the refresh/cancel button is clicked. */
    private void onRefreshCancelButtonClick() {
        if (refreshCancelButton.getDrawable().getConstantState().equals(
                getResources().getDrawable(R.drawable.twitt4droid_ic_cancel_holo_dark).getConstantState())) {
            webView.stopLoading();
            refreshCancelButton.setContentDescription(getString(R.string.twitt4droid_refresh_button_title));
            refreshCancelButton.setImageResource(R.drawable.twitt4droid_ic_refresh_holo_dark);
        } else {
            webView.reload();
            refreshCancelButton.setContentDescription(getString(R.string.twitt4droid_cancel_button_title));
            refreshCancelButton.setImageResource(R.drawable.twitt4droid_ic_cancel_holo_dark);
        }
    }
}