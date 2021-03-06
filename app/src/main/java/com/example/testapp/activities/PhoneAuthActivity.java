package com.example.testapp.activities;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.testapp.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

public class PhoneAuthActivity extends AppCompatActivity implements View.OnClickListener,View.OnTouchListener{
    private EditText mophone, pswd;
    TextView skip, verify_button, resend_token;
    private FirebaseAuth mAuth;
    private static final String TAG = "signup";
    private boolean mVerificationInProgress = false;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    ProgressBar progressBar;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private static final int STATE_CODE_SENT = 1;
    private static final int STATE_SIGNIN_SUCCESS = 2;
    private static final int STATE_SIGNIN_FAILED = 3;
    private static final int STATE_VERIFY_SUCCESS = 4;

    private FirebaseFirestore rootRef;

    public static Activity activity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_auth);

        activity = this;

        //Layout rendering
        verify_button = (TextView) findViewById(R.id.verify_button);
        skip = (TextView) findViewById(R.id.phone_auth_skip);
        pswd = (EditText) findViewById(R.id.pswrdd);
        mophone = (EditText) findViewById(R.id.mobphone);
        resend_token = findViewById(R.id.resend_token);
        progressBar = findViewById(R.id.phone_auth_progress);
        Typeface custom_font = Typeface.createFromAsset(getAssets(), "fonts/LatoLight.ttf");
        Typeface custom_font1 = Typeface.createFromAsset(getAssets(), "fonts/LatoRegular.ttf");
        mophone.setTypeface(custom_font);
        verify_button.setTypeface(custom_font1);
        pswd.setTypeface(custom_font);
        skip.setTypeface(custom_font);
        resend_token.setTypeface(custom_font1);

        rootRef = FirebaseFirestore.getInstance();



        skip.setOnClickListener(this);
        verify_button.setOnTouchListener(this);
        resend_token.setOnTouchListener(this);


        //Firebase Auth
        mAuth = FirebaseAuth.getInstance();


        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {

                Log.d(TAG, "onVerificationCompleted:" + credential);
                mVerificationInProgress = false;
                updateUI(STATE_VERIFY_SUCCESS);
                signInWithPhoneAuthCredential(credential);
            }

            @Override
            public void onVerificationFailed(FirebaseException e) {
                // This callback is invoked in an invalid request for verification is made,
                // for instance if the the phone number format is not valid.
                Log.w(TAG, "onVerificationFailed", e);
                // [START_EXCLUDE silent]
                mVerificationInProgress = false;
                // [END_EXCLUDE]
                progressBar.setVisibility(View.GONE);
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    // Invalid request
                    // [START_EXCLUDE]
                    mophone.setError("Invalid phone number.");
                    // [END_EXCLUDE]
                } else if (e instanceof FirebaseTooManyRequestsException) {
                }
            }

            @Override
            public void onCodeSent(@NonNull String verificationId,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                progressBar.setVisibility(View.GONE);
                mVerificationId = verificationId;
                mResendToken = token;
                updateUI(STATE_CODE_SENT);
            }
        };
    }

    private void updateUI(int state){
        FirebaseUser user;
        switch (state){
            case STATE_CODE_SENT:
                LinearLayout otp = findViewById(R.id.otp);
                otp.setVisibility(View.VISIBLE);
                resend_token.setVisibility(View.VISIBLE);
                verify_button.setText("SignIn");
                Toast.makeText(activity, "OTP Sent! Check your inbox", Toast.LENGTH_SHORT).show();
                break;
            case STATE_SIGNIN_SUCCESS:
//
                user = mAuth.getCurrentUser();
                if(user!=null)
                    Toast.makeText(PhoneAuthActivity.this, user.getDisplayName(),
                            Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(PhoneAuthActivity.this, "Something's not quite right",
                            Toast.LENGTH_SHORT).show();
                break;
            case STATE_SIGNIN_FAILED:
                Toast.makeText(PhoneAuthActivity.this, "SignIn Failed", Toast.LENGTH_SHORT).show();
                break;
            case STATE_VERIFY_SUCCESS:
//                pswd.setText();

        }
    }


    private boolean validatePhoneNumber() {
        String phoneNumber = mophone.getText().toString();
        if (TextUtils.isEmpty(phoneNumber)) {
            mophone.setError("Can't be empty");
            return false;
        }
        if(phoneNumber.length() != 10){
            mophone.setError("Enter 10 digits");
            return false;
        }
        return true;
    }

    private void startPhoneNumberVerification(String phoneNumber) {
        startPhoneNumberVerification(phoneNumber, "+91");
    }

    private void startPhoneNumberVerification(String phoneNumber, String country_code) {
        // [START start_phone_auth]
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                country_code + phoneNumber,        // Phone number to verify
                60,                 // Timeout duration
                TimeUnit.SECONDS,   // Unit of timeout
                this,               // Activity (for callback binding)
                mCallbacks);        // OnVerificationStateChangedCallbacks
        // [END start_phone_auth]

        mVerificationInProgress = true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN){
            v.setBackgroundColor(getResources().getColor(R.color.auth_button_light));
        }
        if(event.getAction() == MotionEvent.ACTION_UP){
            v.setBackgroundColor(getResources().getColor(R.color.auth_button));
            switch (v.getId()){
                case R.id.resend_token:
                    progressBar.setVisibility(View.VISIBLE);
                    resendVerificationCode(mophone.getText().toString(), mResendToken);
                    break;
                case R.id.verify_button:
                    if(verify_button.getText() == "SignIn"){
                        progressBar.setVisibility(View.VISIBLE);
                        String code = pswd.getText().toString();
                        if (TextUtils.isEmpty(code)) {
                            pswd.setError("Cannot be empty.");
                        }else {
                            verifyPhoneNumberWithCode(mVerificationId, code);
                            break;
                        }
                    }
                    if(validatePhoneNumber()) {
                        progressBar.setVisibility(View.VISIBLE);
                        startPhoneNumberVerification(mophone.getText().toString());
                    }
                    break;
            }
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.phone_auth_skip:
                Intent it = new Intent(PhoneAuthActivity.this, ExploreActivity.class);
                startActivity(it);
                finish();
                LoginActivity.activity.finish();
                break;
        }

    }

    private void resendVerificationCode(String phoneNumber,
                                        PhoneAuthProvider.ForceResendingToken token) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                "+91" + phoneNumber,        // Phone number to verify
                60,                 // Timeout duration
                TimeUnit.SECONDS,   // Unit of timeout
                this,               // Activity (for callback binding)
                mCallbacks,         // OnVerificationStateChangedCallbacks
                token);             // ForceResendingToken from callbacks

    }

    private void verifyPhoneNumberWithCode(String verificationId, String code) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {

                        if (task.isSuccessful()) {
//                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = task.getResult().getUser();

                            if(user!=null) {
//                                Toast.makeText(phoneAuth.this, "yooo " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                                Log.d(TAG, "here we are sir");
                                if(rootRef == null)
                                    return;
                                Log.d(TAG, "here we are sir1");
                                rootRef.collection("users")
                                        .document(user.getUid())
                                        .get()
                                        .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {

                                            @Override
                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                progressBar.setVisibility(View.GONE);
                                                Intent i;
                                                if (task.isSuccessful()) {
                                                    Log.d(TAG, "here we are sir2");
                                                    DocumentSnapshot document = task.getResult();
                                                    if (document.exists()) {
                                                        Log.d(TAG, "DocumentSnapshot data: " + document.getData());
                                                        if(document.get("name").toString() == null || document.get("name").toString() == "") {
                                                            i = new Intent(PhoneAuthActivity.this, UserNameActivity.class);
                                                            startActivity(i);
                                                        }
                                                        else{
                                                            i = new Intent(PhoneAuthActivity.this, ExploreActivity.class);
                                                            startActivity(i);
                                                            finish();
                                                            LoginActivity.activity.finish();
                                                        }
                                                    } else {
                                                        Log.d(TAG, "No such document");
                                                        i = new Intent(PhoneAuthActivity.this, UserNameActivity.class);
                                                        startActivity(i);
                                                    }
                                                } else {
                                                    Log.d(TAG, "get failed with ", task.getException());
                                                }
                                            }
                                        });
//                                updateUI(STATE_SIGNIN_SUCCESS);
                            }
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            if (task.getException() instanceof
                                    FirebaseAuthInvalidCredentialsException) {
                                pswd.setError("Invalid code.");
                            }
                            updateUI(STATE_SIGNIN_FAILED);
                        }
                        progressBar.setVisibility(View.GONE);
                    }
                });

    }


}
