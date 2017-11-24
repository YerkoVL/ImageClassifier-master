package com.app.androidkt.tensorf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.ibm.mobilefirstplatform.clientsdk.android.analytics.api.Analytics;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.Response;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.ResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.logger.api.Logger;
import com.ibm.watson.developer_cloud.conversation.v1.ConversationService;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ChatActivity extends Activity {

    private RecyclerView recyclerView;
    private ChatAdapter mAdapter;
    private ArrayList messageArrayList;
    private EditText inputMessage;
    private ImageButton btnSend;
    private Map<String,Object> context = new HashMap<>();
    private boolean initialRequest;
    private Logger myLogger;
    private Context mContext;
    private String workspace_id;
    private String conversation_username;
    private String conversation_password;
    private String analytics_APIKEY;

    String inputmessage = "";
    int flag = 0,aux=0, flagSiniestro = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        mContext = getApplicationContext();

        mContext = getApplicationContext();
        conversation_username = mContext.getString(R.string.conversation_username);
        conversation_password = mContext.getString(R.string.conversation_password);
        workspace_id = mContext.getString(R.string.workspace_id);
        analytics_APIKEY = mContext.getString(R.string.mobileanalytics_apikey);

        //Bluemix Mobile Analytics
        BMSClient.getInstance().initialize(getApplicationContext(), BMSClient.REGION_US_SOUTH);
        //Analytics is configured to record lifecycle events.
        Analytics.init(getApplication(), "WatBot", analytics_APIKEY, false, Analytics.DeviceEvent.ALL);
        //Analytics.send();
        myLogger = Logger.getLogger("myLogger");
        // Send recorded usage analytics to the Mobile Analytics Service
        Analytics.send(new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                // Handle Analytics send success here.
            }

            @Override
            public void onFailure(Response response, Throwable throwable, JSONObject jsonObject) {
                // Handle Analytics send failure here.
            }
        });

        // Send logs to the Mobile Analytics Service
        Logger.send(new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                // Handle Logger send success here.
            }

            @Override
            public void onFailure(Response response, Throwable throwable, JSONObject jsonObject) {
                // Handle Logger send failure here.
            }
        });

        inputMessage = (EditText) findViewById(R.id.message);
        btnSend = (ImageButton) findViewById(R.id.btn_send);
        String customFont = "Montserrat-Regular.ttf";
        Typeface typeface = Typeface.createFromAsset(getAssets(), customFont);
        inputMessage.setTypeface(typeface);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        messageArrayList = new ArrayList<>();
        mAdapter = new ChatAdapter(messageArrayList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);
        this.inputMessage.setText("");
        this.initialRequest = true;
        sendMessage("Hola");

        btnSend.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(checkInternetConnection()) {
                    sendMessageContinued();
                }
            }
        });
    }

    private boolean checkInternetConnection() {
        // get Connectivity Manager object to check connection
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        // Check for network connections
        if (isConnected){
            return true;
        }
        else {
            Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void sendMessage(String mensaje) {

        inputmessage = mensaje;//this.inputMessage.getText().toString().trim();

        this.inputMessage.setText("");
        mAdapter.notifyDataSetChanged();

        new sendMessageChat().execute();
    }

    private void sendMessageContinued() {

        inputmessage = this.inputMessage.getText().toString().trim();
        if(!inputmessage.equals("")) {
            Message inputMessage = new Message();
            if(aux>=1){
                inputMessage.setMessage("");
                inputMessage.setId("1");
                //messageArrayList.add(inputMessage);
            }else{
                inputMessage.setMessage(inputmessage);
                inputMessage.setId("1");
                messageArrayList.add(inputMessage);
            }
            //if(getFlag()) {

            //}
            myLogger.info("Enviando mensaje al Watson Conversation Service");

            this.inputMessage.setText("");
            mAdapter.notifyDataSetChanged();

            new sendMessageChat().execute();
        }else{
            Toast.makeText(mContext,"No puede enviar un mensaje vacio",Toast.LENGTH_SHORT).show();
        }
    }

    private class sendMessageChat extends AsyncTask<String,Void,Boolean> {
        MessageResponse response;

        @Override
        protected Boolean doInBackground(String... strings) {
            ConversationService service = new ConversationService(ConversationService.VERSION_DATE_2016_07_11);
            service.setUsernameAndPassword(conversation_username, conversation_password);
            MessageRequest newMessage = new MessageRequest.Builder().inputText(inputmessage).context(context).build();
            response = service.message(workspace_id, newMessage).execute();

            //Passing Context of last conversation
            if (response.getContext() != null) {
                context.clear();
                context = response.getContext();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            try{
                    /*if(response.getContext() !=null)
                    {
                        context.clear();
                        context = response.getContext();
                    }*/
                Message outMessage=new Message();
                if(response!=null)
                {
                    if(response.getOutput()!=null && response.getOutput().containsKey("text"))
                    {
                        ArrayList responseList = (ArrayList) response.getOutput().get("text");
                        if(null !=responseList && responseList.size()>0) {
                            String mensageResponse;
                            mensageResponse = (String) responseList.get(0);
                            outMessage.setMessage(sendKey(mensageResponse));
                            outMessage.setId("2");
                            //messageArrayList.add(outMessage);
                        }
                    }
                    //if(aux==1) {
                        //String mensageResponse = "Su caso será derivada al Call Center del CEA.";
                        //outMessage.setMessage(mensageResponse);
                        //outMessage.setId("2");
                    //}

                    messageArrayList.add(outMessage);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            mAdapter.notifyDataSetChanged();
                            if (mAdapter.getItemCount() > 1) {
                                recyclerView.getLayoutManager().smoothScrollToPosition(recyclerView, null, mAdapter.getItemCount()-1);
                                if(flag==1) {
                                    new CountDownTimer(3000, 1000) {

                                        public void onTick(long millisUntilFinished) {
                                        }

                                        public void onFinish() {
                                            deleteSP();
                                            startActivity(new Intent(ChatActivity.this,MainActivity.class));
                                        }
                                    }.start();
                                }
                                if(flagSiniestro==1){
                                    new CountDownTimer(3000, 1000) {

                                        public void onTick(long millisUntilFinished) {
                                        }

                                        public void onFinish() {
                                            deleteSP();
                                            startActivity(new Intent(ChatActivity.this,DerivativeActivity.class));
                                        }
                                    }.start();
                                }else if(flagSiniestro==2){
                                    new CountDownTimer(3000, 1000) {

                                        public void onTick(long millisUntilFinished) {
                                        }

                                        public void onFinish() {
                                            deleteSP();
                                            startActivity(new Intent(ChatActivity.this,MapActivity.class));
                                        }
                                    }.start();
                                }
                                /*
                                else if(getFlagMessage()==1 && aux >=1){
                                    new CountDownTimer(3000, 1000) {

                                        public void onTick(long millisUntilFinished) {
                                        }

                                        public void onFinish() {
                                            deleteSP();
                                            startActivity(new Intent(ChatActivity.this,DerivativeActivity.class));
                                        }
                                    }.start();
                                }else if(getFlagMessage()==2){
                                    new CountDownTimer(3000, 1000) {

                                        public void onTick(long millisUntilFinished) {
                                        }

                                        public void onFinish() {
                                            deleteSP();
                                            startActivity(new Intent(ChatActivity.this,DerivativeActivity.class));
                                        }
                                    }.start();
                                }*/
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String sendKey(String key){
        String[] newKey = key.split(",");
        if(newKey.length>1) {
            if (newKey[1].equals("doggy")) {
                flag = 1;
                key = newKey[0];
            } else {
                flag = 0;
            }
            if (newKey[1].equals("leve")) {
                flagSiniestro= 1;
                key = newKey[0];
            }else if (newKey[1].equals("grave")){
                flagSiniestro= 2;
                key = newKey[0];
            }else{
                flagSiniestro= 0;
            }
        }

        return key;
    }

    public void deleteSP(){
        PreferenceManager.getDefaultSharedPreferences(ChatActivity.this).edit().clear().apply();
    }

        public String getMessage(){
        float inputMessage = 0;
        String resultFinal = "";
        try {
            SharedPreferences prefs = getSharedPreferences("MyChat", Context.MODE_PRIVATE);
            inputMessage = prefs.getFloat("trasera", 0);
            if (inputMessage>=0){//ARREGLAR YERKO
                resultFinal = "siniestro";
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return resultFinal;
    }

    public boolean getFlag(){
        boolean flag = true;
        try {
            SharedPreferences prefs = getSharedPreferences("MyChat", Context.MODE_PRIVATE);
            int inputFlag = prefs.getInt("inputFlag", 0);
            if(inputFlag!=0){
                flag = false;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return flag;
    }

    public int getFlagMessage(){
        int inputFlagMessage = 0;
        try {
            SharedPreferences prefs = getSharedPreferences("MyChat", Context.MODE_PRIVATE);
            inputFlagMessage = prefs.getInt("inputFlagMessage", 0);
        }catch (Exception e){
            e.printStackTrace();
        }
        return inputFlagMessage;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        aux ++;
        //inputMessage.setText(getMessage());
        sendMessage(getMessage());
    }

}
