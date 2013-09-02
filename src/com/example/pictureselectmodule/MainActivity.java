package com.example.pictureselectmodule;

//import com.example.dodedodo.XMPPService;

import java.io.File;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.view.GestureDetectorCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;


public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private static final String MODULE_NAME = "PictureSelectModule";
	
	TextView mCallbackText;
	EditText mEditText;
	Button mButtonSend;
	Button mButtonImage;
	Button mButtonLogin;
	ImageView mImageView;
	
	
	Messenger mToMsgService = null;
	final Messenger mFromMsgService = new Messenger(new IncomingMsgHandler());
	/** Flag indicating whether we have called bind on the service. */
	boolean mMsgServiceIsBound;
	
	Messenger mPortOutMessenger = null;
	
	private GestureDetectorCompat mGestureDetector;

	
	// Copied from MsgService, should be an include?
	public static final int MSG_REGISTER = 1;
	public static final int MSG_UNREGISTER = 2;
	public static final int MSG_SET_MESSENGER = 3;
	public static final int MSG_START = 4;
	public static final int MSG_STOP = 5;
	public static final int MSG_SEND = 6;
	public static final int MSG_XMPP_LOGIN = 7;
	public static final int MSG_ADD_PORT = 8;
	public static final int MSG_REM_PORT = 9;
	public static final int MSG_XMPP_LOGGED_IN = 10;
	public static final int MSG_XMPP_DISCONNECTED = 11;
	public static final int MSG_PORT_DATA = 12;
	public static final int MSG_USER_LOGIN = 13;
	public static final int MSG_GET_MESSENGER = 14;
		
	public static final int DATATYPE_FLOAT = 1;
	public static final int DATATYPE_FLOAT_ARRAY = 2;
	public static final int DATATYPE_STRING = 3;
	public static final int DATATYPE_IMAGE = 4;
	public static final int DATATYPE_BINARY = 5;

	// onCreate -> onStart -> onResume
	// onPause -> onResume
	// onPause -> onStop -> onRestart -> onStart -> onResume
	// onPause -> onStop -> onDestroy

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG,"onCreate");
		setContentView(R.layout.activity_main);
		
		mCallbackText = (TextView) findViewById(R.id.messageOutput);
		mEditText = (EditText) findViewById(R.id.messageInput);
		mButtonSend = (Button) findViewById(R.id.buttonSend);
		mButtonImage = (Button) findViewById(R.id.buttonImage);
		mButtonLogin = (Button) findViewById(R.id.buttonLogin);
		mImageView = (ImageView) findViewById(R.id.imageView);
		
		mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        if (actionId == R.id.sendMsg || actionId == EditorInfo.IME_ACTION_SEND) {
		            sendMessage();
		            return true;
		        }
		        return false;
		    }
		});
		
		mButtonSend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
		        sendMessage();
		    }
		});
		
		mButtonLogin.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
//		        login();
		    }
		});
		
		mButtonImage.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				selectImage();
			}
		});
		
		mGestureDetector = new GestureDetectorCompat(this, new MyGestureListener());
		
//		Intent intent = new Intent(this, LoginActivity.class);
//		startActivityForResult(intent, LOGIN_REPLY);
		
		doBindService();
	}
	
	@Override
	public void onStart() {
		super.onStart();
		Log.i(TAG,"onStart");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.i(TAG,"onResume");
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.i(TAG,"onPause");
	}

	@Override
	public void onStop() {
		super.onStop();
		Log.i(TAG,"onStop");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy " + mMsgServiceIsBound);
		doUnbindService();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private ServiceConnection mMsgServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been established, giving us the service object
			// we can use to interact with the service.  We are communicating with our service through an IDL 
			// interface, so get a client-side representation of that from the raw service object.
			mToMsgService = new Messenger(service);
			mCallbackText.setText("Connected to Dodedodo.");

			Message msg = Message.obtain(null, MSG_REGISTER);
			Bundle bundle = new Bundle();
			bundle.putString("module", MODULE_NAME);
			bundle.putInt("id", 0);
			msg.setData(bundle);
			msgSend(msg);
			
//	        Toast.makeText(Binding.this, R.string.remote_service_connected, Toast.LENGTH_SHORT).show();
			Log.i(TAG, "Connected to MsgService: " + mToMsgService.toString());
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected: its process crashed.
			mToMsgService = null;
			mCallbackText.setText("Disconnected from Dodedodo.");

//	        Toast.makeText(Binding.this, R.string.remote_service_disconnected, Toast.LENGTH_SHORT).show();
			Log.i(TAG, "Disconnected from MsgService");
		}
	};

	// Handle messages from MsgService
	class IncomingMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_SET_MESSENGER:
				Log.i(TAG, "set port: " + msg.getData().getString("port") + " to: " + msg.replyTo.toString());
				if (msg.getData().getString("port").equals("out"))
					mPortOutMessenger = msg.replyTo;
				break;
			case MSG_STOP:
				Log.i(TAG, "stopping");
				finish();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	void doBindService() {
		// Establish a connection with the service.  We use an explicit class name because there is no reason to be 
		// able to let other applications replace our component.
		//bindService(new Intent(this, XMPPService.class), mConnection, Context.BIND_AUTO_CREATE);
		
		Intent intent = new Intent();
//		intent.setComponent(new ComponentName("com.example.dodedodo", "com.example.dodedodo.XMPPService"));
		intent.setClassName("com.example.dodedodo", "com.example.dodedodo.MsgService");
//		intent.setClassName("com.example.dodedodo", "XMPPService");
		bindService(intent, mMsgServiceConnection, Context.BIND_AUTO_CREATE);
//		bindService(new Intent(this, XMPPService.class), mConnection, Context.BIND_AUTO_CREATE);
//		Log.i(TAG, "binding to: " + XMPPService.class.toString());
		mMsgServiceIsBound = true;
		mCallbackText.setText("Binding to service.");
	}

	void doUnbindService() {
		if (mMsgServiceIsBound) {
			// If we have received the service, and registered with it, then now is the time to unregister.
			if (mToMsgService != null) {
				Message msg = Message.obtain(null, MSG_UNREGISTER);
				Bundle bundle = new Bundle();
				bundle.putString("module", MODULE_NAME);
				bundle.putInt("id", 0);
				msg.setData(bundle);
				msgSend(msg);
			}
			// Detach our existing connection.
			unbindService(mMsgServiceConnection);
			mMsgServiceIsBound = false;
			mCallbackText.setText("Unbinding from service.");
		}
	}

	protected void msgSend(Message msg) {
		if (!mMsgServiceIsBound) {
			Log.i(TAG, "Can't send message to service: not bound");
			return;
		}
		try {
			msg.replyTo = mFromMsgService;
			mToMsgService.send(msg);
		} catch (RemoteException e) {
			Log.i(TAG, "Failed to send msg to service. " + e);
			// There is nothing special we need to do if the service has crashed.
		}
	}
	
	protected void msgSend(Messenger messenger, Message msg) {
		if (messenger == null)
			return;
		try {
			msg.replyTo = mFromMsgService;
			messenger.send(msg);
		} catch (RemoteException e) {
			Log.i(TAG, "failed to send msg to service. " + e);
			// There is nothing special we need to do if the service has crashed.
		}
	}

	public void sendMessage() {
		// Do something in response to button click
		String text = mEditText.getText().toString();
		if (TextUtils.isEmpty(text))
			return;
		Message msg = Message.obtain(null, MSG_PORT_DATA);
		Bundle bundle = new Bundle();
		bundle.putInt("datatype", DATATYPE_STRING);
		bundle.putString("data", text);
		msg.setData(bundle);
		msgSend(mPortOutMessenger, msg);
		mEditText.getText().clear();
	}

	
	////////////////////////////////////////////////////////////
	//                     Select image                       //
	////////////////////////////////////////////////////////////
	public void selectImage() {
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
	}
	
	private static final int PICK_IMAGE = 1;
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == PICK_IMAGE && data != null && data.getData() != null) {
			Uri selectedImage = data.getData();
			String[] columnFilter = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.ORIENTATION};
//			String orientationColumn = MediaStore.Images.Media.ORIENTATION

			Cursor cursor = getContentResolver().query(selectedImage, columnFilter, null, null, null);
			if (!cursor.moveToFirst())
				return;

			int columnIndex = cursor.getColumnIndex(columnFilter[0]);
			if (columnIndex < 0)
				return;
			final String filePath = cursor.getString(columnIndex);
			
			columnIndex = cursor.getColumnIndex(columnFilter[1]);
			if (columnIndex < 0)
				return;
			// The orientation for the image expressed as degrees. Only degrees 0, 90, 180, 270 will work. 
			final int rotation = cursor.getInt(columnIndex);
			
			cursor.close();
			
			Bitmap selectedImageBitmap = BitmapFactory.decodeFile(filePath);
			mImageView.setImageBitmap(selectedImageBitmap);
			

			Log.i(TAG, "image: " + filePath + " rotation:" + rotation);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	
	////////////////////////////////////////////////////////////
	//                       Gestures                         //
	////////////////////////////////////////////////////////////
    @Override 
    public boolean onTouchEvent(MotionEvent event){ 
        this.mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

	class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(MotionEvent event) {
			return true;
		}
		
		@Override
		public boolean onSingleTapUp(MotionEvent event) {
//			PointerCoords coords = new PointerCoords();
//			event.getPointerCoords(0, coords);
//			Log.i(TAG, coords.x + " " + coords.y);
			Log.i(TAG, event.getX() + " " + event.getY());
//			Log.i(TAG, event.toString());
			
			Message msg = Message.obtain(null, MSG_PORT_DATA);
			Bundle bundle = new Bundle();
			bundle.putInt("datatype", DATATYPE_FLOAT_ARRAY);
			float[] data = new float[2];
			data[0] = event.getX();
			data[1] = event.getY();
			bundle.putFloatArray("data", data);
			msg.setData(bundle);
			msgSend(mPortOutMessenger, msg);
			
			return true;
		}
	}

//	public void stopApp(View view) {
//		// Do something in response to button click
//		doUnbindService();
//		Log.i(TAG, "stopping service..");
//		stopService(new Intent(this, XMPPService.class));
//	}
	
	


}
