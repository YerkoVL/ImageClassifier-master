package com.app.androidkt.tensorf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;

import dmax.dialog.SpotsDialog;

public class LoginActivity extends Activity {
    Button enter;
    Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mContext = this;

        enter = (Button) findViewById(R.id.btn_enter);

        enter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final android.app.AlertDialog xdialog = new SpotsDialog(mContext);
                //startActivity(new Intent(LoginActivity.this,MainActivity.class));
                new CountDownTimer(4000, 1000) {
                    public void onTick(long millisUntilFinished) {
                        xdialog.show();
                        xdialog.setMessage("Validando Placa");
                    }

                    public void onFinish() {
                        xdialog.dismiss();
                        startActivity(new Intent(LoginActivity.this,ChatActivity.class));
                    }
                }.start();
            }
        });
    }
}
