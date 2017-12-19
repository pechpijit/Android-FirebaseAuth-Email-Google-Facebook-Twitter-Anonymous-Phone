package com.khiancode.supat.managementfishtank.multilogin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.TwitterAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.khiancode.supat.managementfishtank.BaseActivity;
import com.khiancode.supat.managementfishtank.MainActivity;
import com.khiancode.supat.managementfishtank.R;
import com.khiancode.supat.managementfishtank.model.UserProfile;
import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;
import com.twitter.sdk.android.core.identity.TwitterLoginButton;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.fabric.sdk.android.Fabric;

public class OtherLoginActivity extends BaseActivity {

    final private static String TAG = "OtherLoginActivity";

    private static int RC_SIGN_IN = 1604;
    private static int REQUEST_CODE_FSQ_CONNECT = 8989;

    private FirebaseAuth mAuth;

    private GoogleApiClient mGoogleApiClient;

    private CallbackManager mCallbackManagerFB;

    private TwitterLoginButton mLoginButtonTwitter;

    private boolean twitterLogin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        printHashKey();
        FacebookSdk.sdkInitialize(getApplicationContext());
        Twitter.initialize(this);
        setContentView(R.layout.activity_other_login);
//        requestCACHE();

        mAuth = FirebaseAuth.getInstance();

        mLoginButtonTwitter = findViewById(R.id.login_twitter);
        mLoginButtonTwitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                twitterLogin = true;
            }
        });
        mLoginButtonTwitter.setCallback(new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                Log.d(TAG, "twitterLogin:success" + result);
                final Result<TwitterSession> resultTw = result;
                final TwitterSession twitterSession = result.data;
                TwitterAuthClient authClient = new TwitterAuthClient();
                authClient.requestEmail(twitterSession, new com.twitter.sdk.android.core.Callback<String>() {
                    @Override
                    public void success(Result<String> emailResult) {
                        String email = emailResult.data;
                        handleTwitterSession(resultTw.data,email);
                    }

                    @Override
                    public void failure(TwitterException e) {
                        Crashlytics.logException(e);
                        LoginManager.getInstance().logOut();
                        dialogTM("ไม่สามารถเข้าสู่ระบบได้", "ขออภัย เกิดข้อผิดพลาดบางอย่าง ระบบไม่สามารถดึงที่อยู่อีเมล์ได้ กรุณาลองใหม่อีกครั้งหรือติดต่อผู้พัฒนา");
                        hideProgressDialog();
                    }
                });

            }

            @Override
            public void failure(TwitterException exception) {
                Log.w(TAG, "twitterLogin:failure", exception);
                Crashlytics.logException(exception);
            }
        });

        LoginButton loginButton = findViewById(R.id.login_facebook);
        loginButton.setReadPermissions("email", "public_profile");
//        loginButton.setReadPermissions(Arrays.asList(
//                "public_profile", "email"));
        mCallbackManagerFB = CallbackManager.Factory.create();
        loginButton.registerCallback(mCallbackManagerFB, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(final LoginResult loginResult) {
                Log.d(TAG, "facebook:onSuccess:" + loginResult);
                final LoginResult result = loginResult;
                GraphRequest request = GraphRequest.newMeRequest(
                        loginResult.getAccessToken(),
                        new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(JSONObject object, GraphResponse response) {
                                Log.v(TAG, response.toString());
                                String email = object.optString("email");
                                handleFacebookAccessToken(result.getAccessToken(), email);
                            }
                        });
                Bundle parameters = new Bundle();
                parameters.putString("fields", "last_name,first_name,email");
                request.setParameters(parameters);
                request.executeAsync();
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "facebook:onCancel");
            }

            @Override
            public void onError(FacebookException error) {
                Crashlytics.logException(error);
                Log.d(TAG, "facebook:onError", error);
            }
        });

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(OtherLoginActivity.this, null)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        findViewById(R.id.login_google).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClickGoogleSignIn");
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });

        BroadcastReceiver broadcast_reciever = new BroadcastReceiver() {

            @Override
            public void onReceive(Context arg0, Intent intent) {
                String action = intent.getAction();
                if (action.equals("finish_activity")) {
                    finish();
                }
            }
        };
        registerReceiver(broadcast_reciever, new IntentFilter("finish_activity"));

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                Toast.makeText(this, "Google Sign In failed", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (twitterLogin) {
            twitterLogin = false;
            mLoginButtonTwitter.onActivityResult(requestCode, resultCode, data);
            return;
        }

        mCallbackManagerFB.onActivityResult(requestCode, resultCode, data);
    }

    private void firebaseAuthWithGoogle(final GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());
        showProgressDialog(AUTH);
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "firebaseAuthWithGoogle:success");
                            onSocialLoginSuccess(mAuth.getCurrentUser(), UserProfile.GOOGLE, acct.getEmail());
                        } else if (task.getException() instanceof FirebaseNetworkException) {
                            Log.d(TAG, "firebaseAuthWithGoogle:FirebaseNetworkException");
                            onFirebaseNetworkException();
                        } else {
                            Log.w(TAG, "firebaseAuthWithGoogle:failure", task.getException());
                            onFirebaseAuthFailed(task.getException());
                        }

                        hideProgressDialog();
                    }
                });
    }

    private void handleFacebookAccessToken(final AccessToken token, final String email) {
        Log.d(TAG, "handleFacebookAccessToken:" + token);
        showProgressDialog(AUTH);

        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "handleFacebookAccessToken:success");
                            onSocialLoginSuccess(mAuth.getCurrentUser(), UserProfile.FACEBOOK, email);
                        } else if (task.getException() instanceof FirebaseNetworkException) {
                            Log.d(TAG, "handleFacebookAccessToken:FirebaseNetworkException");
                            onFirebaseNetworkException();
                        } else {
                            Log.w(TAG, "handleFacebookAccessToken:failure", task.getException());
                            onFirebaseAuthFailed(task.getException());
                        }

                        hideProgressDialog();
                    }
                });
    }

    private void handleTwitterSession(final TwitterSession session, final String email) {
        Log.d(TAG, "handleTwitterSession:" + session);
        showProgressDialog(AUTH);
        AuthCredential credential = TwitterAuthProvider.getCredential(
                session.getAuthToken().token,
                session.getAuthToken().secret);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "handleTwitterSession:success");
                            onSocialLoginSuccess(mAuth.getCurrentUser(), UserProfile.TWEETER,email);
                        } else if (task.getException() instanceof FirebaseNetworkException) {
                            Log.d(TAG, "handleTwitterSession:FirebaseNetworkException");
                            onFirebaseNetworkException();
                        } else {
                            Log.w(TAG, "handleTwitterSession:failure", task.getException());
                            onFirebaseAuthFailed(task.getException());
                        }
                        hideProgressDialog();
                    }
                });
    }

    public void onClickSignInAnonymous(View view) {
        Log.d(TAG, "onClickSignInAnonymous");
        new AlertDialog.Builder(this, R.style.AppTheme_Dark_Dialog)
                .setTitle("แจ้งเตือน")
                .setMessage("ท่านกำลังเข้าสู่ระบบแบบไม่ระบุตัวตน (ทดลองใช้) ข้อมูลจะสูญหาย หากท่านออกจากระบบโดยไม่ได้ผูกบัญชีกับอีเมล์หรือบริการอื่นๆ")
                .setPositiveButton("ตกลง", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.d(TAG, "Confirm SignInAnonymous.");
                        dialogInterface.dismiss();
                        SignInAnonymous();
                    }
                })
                .setNegativeButton("ยกเลิก", null)
                .setCancelable(true)
                .show();
    }

    private void SignInAnonymous() {
        showProgressDialog(AUTH);
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                            String refreshedToken = FirebaseInstanceId.getInstance().getToken();

                            UserProfile userProfile = new UserProfile();
                            userProfile.setTokenFirebase(refreshedToken);
                            userProfile.setLoginType(UserProfile.ANONYMOUS);

                            DatabaseReference database = FirebaseDatabase.getInstance().getReference(UserProfile.PATH).child(user.getUid());
                            database.setValue(userProfile);

                            startActivity(new Intent(OtherLoginActivity.this, MainActivity.class));
                            finish();
                            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                        } else if (task.getException() instanceof FirebaseNetworkException) {
                            Log.d(TAG, "signInWithCredential:FirebaseNetworkException");
                            onFirebaseNetworkException();
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            onFirebaseAuthFailed(task.getException());
                        }
                        hideProgressDialog();
                    }
                });
    }

    private void onSocialLoginSuccess(FirebaseUser user, int logintype,String email) {

        String refreshedToken = FirebaseInstanceId.getInstance().getToken();

        UserProfile userProfile = new UserProfile();
        userProfile.setName(user.getDisplayName());
        userProfile.setEmail(email);
        userProfile.setImageUri(user.getPhotoUrl() + "");
        userProfile.setTokenFirebase(refreshedToken);
        userProfile.setLoginType(logintype);

        DatabaseReference database = FirebaseDatabase.getInstance().getReference(UserProfile.PATH).child(user.getUid());
        database.setValue(userProfile);

        startActivity(new Intent(OtherLoginActivity.this, MainActivity.class));
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void onFirebaseNetworkException() {
        dialogTM("ไม่สามารถเชื่อมต่อได้", "กรุณาตรวจสอบอินเทอร์เน็ตของท่าน และลองใหม่อีกครั้ง");
        LoginManager.getInstance().logOut();
    }

    private void onFirebaseAuthFailed(Exception exception) {
        Crashlytics.logException(exception);
        LoginManager.getInstance().logOut();
        dialogTM("ไม่สามารถเข้าสู่ระบบได้", "ขออภัย เกิดข้อผิดพลาดบางอย่าง กรุณาลองใหม่อีกครั้งหรือติดต่อผู้พัฒนา");
    }

    public void onClickSignInEmail(View view) {
        Log.d(TAG, "onClickSignInEmail");
        startActivity(new Intent(this, EmailSignInActivity.class));
        overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_left);
    }

    public void onClickSignInPhone(View view) {
        Log.d(TAG, "onClickSignInPhone");
        startActivity(new Intent(this, PhoneSignInActivity.class));
        overridePendingTransition(R.anim.anim_slide_in_left, R.anim.anim_slide_out_left);
    }

    public void printHashKey() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String hashKey = new String(Base64.encode(md.digest(), 0));
                Log.i(TAG, "printHashKey() Hash Key: " + hashKey);
            }
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "printHashKey()", e);
        } catch (Exception e) {
            Log.e(TAG, "printHashKey()", e);
        }
    }

    public void requestCACHE() {
        Permissions.check(this, new String[]{android.Manifest.permission.CALL_PHONE,
                        android.Manifest.permission.CALL_PHONE},
                "Camera and storage permissions are required because...", new Permissions.Options()
                        .setRationaleDialogTitle("Info"),
                new PermissionHandler() {
                    @Override
                    public void onGranted() {
                        Toast.makeText(OtherLoginActivity.this, "Permissions success.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

}
