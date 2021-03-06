package ca.carsonbrown.android.runon;

import java.util.HashMap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.PhoneLookup;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;

public class SpeakSmsService extends Service implements TextToSpeech.OnInitListener, OnUtteranceCompletedListener {
	
	private static final String TAG = "SpeakSmsService";
	
	private SharedPreferences mSharedPrefs;
	private TextToSpeech mTts;
	private String mMessage = null;
	private boolean mPausedMusic = false;
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (checkPreferences()) {
			String message = "";
			try {
				message = buildNotificationString(intent.getStringExtra("originatingAddress"), intent.getStringExtra("messageBody"));
			} catch (NullPointerException e) {
				//error in getting Intent's extra strings, cannot continue
				return;
			}
			mMessage = message;
			mTts = new TextToSpeech(getApplicationContext(), this);
		}
	}
	
	@Override
	public void onDestroy() {
		if (mTts != null) {
			mTts.shutdown();
		}
		super.onDestroy();
	}

	/**
	 * Check if preferences allow this text message to be announced at all.
	 * @return true if allowed, false otherwise
	 */
	private boolean checkPreferences() {
		//check if the app is enabled at all
		if (mSharedPrefs.getBoolean("enable", true)) {
			//check for headset rule
			int policy = Integer.parseInt(mSharedPrefs.getString("run_policy", "1"));
			AudioManager am = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
			boolean headsetEnabled = am.isWiredHeadsetOn() || am.isBluetoothA2dpOn();
			switch(policy) {
			case 1:	//only with headset
				return headsetEnabled;
			case 2: //only without headset
				return !headsetEnabled;
			case 3:
				return true;
			}
			return true;
		} else {
			return false;
		}
	}
	
//	private void speak(String message) {
//		TtsProviderFactory ttsProviderImpl = TtsProviderFactory.getInstance();
//		if (ttsProviderImpl != null) {
//		    ttsProviderImpl.init(getApplicationContext());
//		    ttsProviderImpl.say(message);
//		}
//	}
	

//	private void getContactFromNumber(String number, Context context) {
//		Uri phoneUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
//				Uri.encode(number));
//		Cursor phonesCursor = managedQuery(phoneUri, new String[] {PhoneLookup.DISPLAY_NAME}, null, null);
//		if(phonesCursor != null && phonesCursor.moveToFirst()) {
//			String displayName = phonesCursor.getString(0); // this is the contact name
//		}
//	}
	
	private String buildNotificationString(String originatingAddress, String messageBody) {
		String notificationString = "";
		notificationString += "New message ";

		
		//Log.v(TAG, "say_sender:" + mSharedPrefs.getString("say_sender", "1"));
		
		if (!mSharedPrefs.getString("say_sender", "1").equals("3")) {
			notificationString += "from ";
			if (mSharedPrefs.getString("say_sender", "1").equals("1")) {
				notificationString += contactName(originatingAddress);
			} else {
				notificationString += originatingAddress;
			}
		}
		notificationString += ". ";
		
		if (mSharedPrefs.getBoolean("say_message", true)) {
			notificationString += messageBody;
		}
		
		return notificationString;
	}
	
	private String contactName(String number) {
		//short circuit for the Settings activity test message
		if (number.equals("5551234")) {
			return "John Doe";
		}
		/// number is the phone number
		Uri lookupUri = Uri.withAppendedPath(
		PhoneLookup.CONTENT_FILTER_URI, 
		Uri.encode(number));
		String[] mPhoneNumberProjection = { PhoneLookup._ID, PhoneLookup.NUMBER, PhoneLookup.DISPLAY_NAME };
		Cursor cur = getApplicationContext().getContentResolver().query(lookupUri,mPhoneNumberProjection, null, null, null);
		String displayName = "" + number;
		try {
		   if (cur.moveToFirst()) {
		      displayName = cur.getString(2);
		   }
		} finally {
		if (cur != null)
		   cur.close();
		}
		return displayName;
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS && mMessage != null) {
			
			
			
			HashMap<String, String> params = new HashMap<String, String>();
			params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "" + mMessage.hashCode());
			
			if (mSharedPrefs.getBoolean("mute_notification", true) || mSharedPrefs.getBoolean("pause_music", false)) {
				int result = mTts.setOnUtteranceCompletedListener(this);
				if (result == TextToSpeech.SUCCESS) {
					//callback worked, so we can unmute notifications when
					//we're done talking
					AudioManager am = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
					if (mSharedPrefs.getBoolean("mute_notification", true)) {
						am.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
					}
					if (mSharedPrefs.getBoolean("pause_music", false) && am.isMusicActive()) {
						mPausedMusic = true;
						Intent i;
						i = new Intent("com.android.music.musicservicecommand.togglepause");
						//i.putExtra("command", "pause");
						this.sendBroadcast(i);
					}
				}
			}
			mTts.setLanguage(mTts.getLanguage());
			mTts.speak(mMessage, TextToSpeech.QUEUE_FLUSH, params);
			mMessage=null;
		}
	}

	@Override
	public void onUtteranceCompleted(String utteranceId) {
		AudioManager am = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
		if (mSharedPrefs.getBoolean("mute_notification", true)) {
			am.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);
		}
		if (mSharedPrefs.getBoolean("pause_music", false) && mPausedMusic) {
			mPausedMusic = false;
			Intent i;
			i = new Intent("com.android.music.musicservicecommand.togglepause");
			//i.putExtra("command", "pause");
			this.sendBroadcast(i);
		}
			
	}

}
