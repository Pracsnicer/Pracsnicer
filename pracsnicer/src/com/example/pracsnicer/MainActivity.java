package com.example.pracsnicer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class MainActivity extends Activity implements OnClickListener {

	public static final String EXTRA_MESSAGE = "message";
	public static final String PROPERTY_REG_ID = "registration_id";
	private static final String PROPERTY_APP_VERSION = "appVersion";
	private static final String TAG = "pracsnicer";
	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	// identifica nuestra app de forma única en el servicio GCM
	private String SENDER_ID = "568774684";

	private GoogleCloudMessaging gcm;
	private String regid;

	private Button btnRegistrar;
	private EditText txtUsuario;
	private Context context;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		btnRegistrar = (Button) findViewById(R.id.btnRegistrar);
		txtUsuario = (EditText) findViewById(R.id.txtUsuario);
		btnRegistrar.setOnClickListener(this);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnRegistrar:
			context = getApplicationContext();
			if (checkPlayServices()) {
				gcm = GoogleCloudMessaging.getInstance(this);
				regid = getRegistrationId(context);
				Toast.makeText(this, regid, Toast.LENGTH_LONG).show();
				if (regid.equals("")) {
					TareaRegistroGCM tarea = new TareaRegistroGCM();
					tarea.execute(txtUsuario.getText().toString());
				} else {
					Log.i(TAG, "No se ha encontrado Google Play Services.");
				}
			}
			break;

		}

	}

	private boolean checkPlayServices() {

		// comprobamos si los servicios de google play están disponibles
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this,
						PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				Log.i(TAG, "Dispositivo no soportado.");
				finish();
			}
			return false;
		}
		return true;
	}

	private String getRegistrationId(Context context) {
		final SharedPreferences prefs = getGCMPreferences(context);
		String registrationId = prefs.getString(PROPERTY_REG_ID, "");
		if (registrationId.equals("")) {
			Log.i(TAG, "Registro no encontrado.");
			return "";
		}
		// Comprobar si la aplicación se ha actualizado; si es así, se debe
		// limpiar el ID de registro ya que no hay garantías de que el ID
		// existente vaya a funcionar con la nueva versión.
		int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION,
				Integer.MIN_VALUE);
		int currentVersion = getAppVersion(context);
		if (registeredVersion != currentVersion) {
			Log.i(TAG, "Versión de la app cambiada.");
			return "";
		}
		return registrationId;
	}

	private SharedPreferences getGCMPreferences(Context context) {
		return getSharedPreferences(MainActivity.class.getSimpleName(),
				Context.MODE_PRIVATE);
	}

	private static int getAppVersion(Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			// no debería ocurrir nunca
			throw new RuntimeException("No se puede obtener el nombre del package: " + e);
		}
	}

	private class TareaRegistroGCM extends AsyncTask<String, Integer, String> {

		@Override
		protected String doInBackground(String... params) {

			try {
				if (gcm == null) {
					gcm = GoogleCloudMessaging.getInstance(context);
				}
				// nos registramos en los servidores de GCM
				regid = gcm.register(SENDER_ID);
				Log.d(TAG, "Registrado en GCM: registration_id=" + regid);

				// registramos el ID generado por GCM en nuestro servidor
				boolean registrado = registroServidor(regid);

				// guardar los datos del registro en las preferencias de la app
				if (registrado) {
					setRegistrationId(context, regid);
				}

			} catch (IOException ex) {
				Log.d(TAG, "Error registro en GCM:" + ex.getMessage());
			}
			return null;
		}

	}

	private boolean registroServidor(String regid) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost("http://pracsnicer.esy.es/post.php");

		// cargamos un array con los datos a enviar a nuestro servidor
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);

		// metemos en el array a enviar el ID de registro
		nameValuePairs.add(new BasicNameValuePair("regid", regid));
		try {
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			HttpResponse respuesta = httpclient.execute(httppost);
			HttpEntity entidad = respuesta.getEntity();
			InputStream is = entidad.getContent();
			String resultado = Util.StreamToString(is);

			// El servidor puede generar caracteres especiales '\n' en la
			// respuesta. Debemos limpiarlos para evitar errores.
			resultado = resultado.replaceAll("\\r\\n|\\r|\\n", "");

			Log.i("Respuesta de nuestro servidor:", resultado);

			// devolvemos cierto si la insercion del ID en nuestro servidor
			// ha ido bien
			if (resultado.equals("insercion_ok")) {
				return true;
			}

		} catch (UnsupportedEncodingException e) {

			e.printStackTrace();
			Log.e("registroServidor", "Codificacion no soportada");
		} catch (ClientProtocolException e) {

			e.printStackTrace();
			Log.e("registroServidor", e.getMessage());
		} catch (IOException e) {

			e.printStackTrace();
			Log.e("registroServidor", e.getMessage());
		}

		return false;
	}

	private void setRegistrationId(Context context, String regid) {

		// si hemos registrado correctamente el dispositivo en GCM y hemos
		// almacenado el ID generado en nuestro servidor sin problemas,
		// guardamos el ID en las preferencias de la app para futuros usos

		SharedPreferences prefs = getSharedPreferences(
				MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);

		int appVersion = getAppVersion(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_REG_ID, regid);
		editor.putInt(PROPERTY_APP_VERSION, appVersion);
		editor.commit();
	}

}
