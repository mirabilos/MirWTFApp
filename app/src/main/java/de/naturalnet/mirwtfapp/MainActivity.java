/*-
 * Copyright © 2015, 2016, 2017
 *      Dominik George <nik@naturalnet.de>
 *
 * Provided that these terms and disclaimer and all copyright notices
 * are retained or reproduced in an accompanying document, permission
 * is granted to deal in this work without restriction, including un‐
 * limited rights to use, publicly perform, distribute, sell, modify,
 * merge, give away, or sublicence.
 *
 * This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
 * the utmost extent permitted by applicable law, neither express nor
 * implied; without malicious intent or gross negligence. In no event
 * may a licensor, author or contributor be held liable for indirect,
 * direct, other damage, loss, or other issues arising in any way out
 * of dealing in the work, even if advised of the possibility of such
 * damage or existence of a defect, except proven that it results out
 * of said person’s immediate fault when using the work as intended.
 */

package de.naturalnet.mirwtfapp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.WtfArrayAdapter;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Main activity of the WTF app
 *
 * @author Dominik George <nik@naturalnet.de>
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String ACRONYMS_FILENAME = "acronyms.gz";

    /**
     * Asynchronous task used to download acronyms database from MirBSD.org
     */
    public class WTFDownloadTask extends AsyncTask<String, Integer, String> {
        private Context context;

        /**
         * Constructor for download task
         *
         * @param context gets passed the activity that controls the task, which will always be the parent activity of this class
         */
        public WTFDownloadTask(Context context) {
            this.context = context;
        }

        /**
         * Main method triggered when the async task is run
         *
         * @param whatever Optional string to be passed, not used right now, but itnerface needs it
         * @return Status text after finishing
         */
        @Override
        protected String doInBackground(String... whatever) {
            // Variables for HTTP stream
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            FileInputStream ver_file = null;

            // Version strings
            String new_ver = "";
            String old_ver = "";

            // Download version information
            try {
                // Set up connection via HTTP to known acronyms URL
                URL url = new URL("http://www.mirbsd.org/acronyms.ver");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.connect();

                // Return error and stop if not status 200
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Download error: HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // Set up streams to read and write
                input = connection.getInputStream();

                // Read stream line by line
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                new_ver = reader.readLine();
            } catch (Exception e) {
                // Return any exception to the user
                return e.toString();
            } finally {
                // Clean up streams
                try {
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                    // Closing is not critical
                }

                // Close connection in case server used keep-alive
                if (connection != null)
                    connection.disconnect();
            }

            // Get old version
            try {
                ver_file = new FileInputStream(getFilesDir() + "/" + MainActivity.ACRONYMS_FILENAME);
                BufferedReader ver_file_reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(ver_file)));

                String line = ver_file_reader.readLine();
                while (line != null) {
                    if (line.startsWith(" @(#)$MirOS: www/.linked/acronyms,v ")) {
                        old_ver = line.substring(5);
                        break;
                    }
                    line = ver_file_reader.readLine();
                }
            } catch (Exception e) {
                // Reading version file is not critical
            } finally {
                // Clean up streams
                try {
                    if (ver_file != null)
                        ver_file.close();
                } catch (IOException ignored) {
                    // Closing is not critical
                }
            }

            // Return if version has not changed
            if (new_ver.trim().equals(old_ver.trim())) {
                return "Acronyms are up to date.";
            }

            try {
                // Set up connection via HTTP to known acronyms URL
                URL url = new URL("http://www.mirbsd.org/acronyms.gz");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.connect();

                // Return error and stop if not status 200
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Download error: HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // Reported content length
                int fileLength = connection.getContentLength();

                // Set up streams to read and write
                input = connection.getInputStream();
                output = new FileOutputStream(getFilesDir() + "/" + MainActivity.ACRONYMS_FILENAME);

                // Set up byte array and counters for progress notification
                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // Set progress in dialog
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                // Return any exception to the user
                return e.toString();
            } finally {
                // Clean up streams
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                    // Closing is not critical
                }

                // Close connection in case server used keep-alive
                if (connection != null)
                    connection.disconnect();
            }

            // Message success
            return "Successfully downloaded acronyms database";
        }

        /**
         * Run before execution to set up progress dialog
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog.show();
        }

        /**
         * Run on publishProgress() to update the progress dialog
         *
         * @param progress percentage of downloaded data
         */
        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(progress[0]);
        }

        /**
         * Run after execution, closes progress dialog
         *
         * @param result Result text for toast
         */
        @Override
        protected void onPostExecute(String result) {
            mProgressDialog.dismiss();
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();

            // Reload acronyms after downloading
            Intent updateIntent = new Intent();
            updateIntent.setAction("de.naturalnet.mirwtfapp.ACRONYMS_RELOAD");
            sendBroadcast(updateIntent);
        }
    }

    // Progress dialog used by download task
    ProgressDialog mProgressDialog;

    // Instance of download task inner class
    private WTFDownloadTask wtfDownloadTask;

    // UI elements
    private Button bSearch;
    private ListView lResults;
    private TextView tHeading;
    private AutoCompleteTextView eAcronym;
    private TextView tCats;

    // Result list stuff
    ArrayList<String> results;
    ArrayAdapter<String> resultsAdapter;

    // Data hashtables
    Hashtable<String, ArrayList<String>> acronyms;
    Hashtable<String, String> uppercasables;
    String acronyms_version;

    // Counter for eastercat
    private int cats = 0;

    /**
     * Run when activity is called
     *
     * @param savedInstanceState Passed by Android if activity was run before but task killed
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load layout and fill action bar
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Register code for reloading acronyms on update
        BroadcastReceiver br = new MyBroadcastReceiver();
        IntentFilter filter = new IntentFilter("de.naturalnet.mirwtfapp.ACRONYMS_RELOAD");
        this.registerReceiver(br, filter);

        // Initalise progress dialog
        mProgressDialog = new ProgressDialog(MainActivity.this) {
            @Override
            protected void onCreate(android.os.Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                // get the progress bar the progress dialogue uses
                // from http://stackoverflow.com/a/21815254/2171120 by VallyN
                ProgressBar bar = (ProgressBar) findViewById(android.R.id.progress);
                // set colour on progress bar
                // from http://stackoverflow.com/a/15809803/2171120 by Torben Kohlmeier
                bar.getProgressDrawable().setColorFilter(0xFF22C819 /*XXX FIXME getColor(R.color.colorAccent)*/, android.graphics.PorterDuff.Mode.SRC_IN);
                bar.getIndeterminateDrawable().setColorFilter(0xFF22C819 /*XXX FIXME getColor(R.color.colorAccent)*/, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        };
        mProgressDialog.setMessage("Downloading acronyms…");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);

        // Activate search button
        bSearch = (Button) findViewById(R.id.bSearch);
        bSearch.setOnClickListener(this);

        // Find text UI elements
        lResults = (ListView) findViewById(R.id.lResults);
        tHeading = (TextView) findViewById(R.id.tHeading);
        eAcronym = (AutoCompleteTextView) findViewById(R.id.eAcronym);
        tCats = (TextView) findViewById(R.id.tCats);

        // Focus text field on start
        eAcronym.requestFocus();

        // Initalise results list
        results = new ArrayList<>();
        resultsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, results);
        lResults.setAdapter(resultsAdapter);

        // Set up listener for Enter key in text field
        TextView.OnEditorActionListener acronymEnterListener = new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    try {
                        // Do acronyms search
                        doWTFSearch();
                    } catch (Exception e) {
                        // Show toast with error if acronyms database could not be searched
                        Toast.makeText(MainActivity.this, "Error searching acronyms database", Toast.LENGTH_LONG).show();
                    }
                }
                return true;
            }
        };
        eAcronym.setOnEditorActionListener(acronymEnterListener);

        // Auto search on tap in auto-complete list
        // FIXME deduplicate with above listener
        eAcronym.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    // Do acronyms search
                    doWTFSearch();
                } catch (Exception e) {
                    // Show toast with error if acronyms database could not be searched
                    Toast.makeText(MainActivity.this, "Error searching acronyms database", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Check for existence of acronyms file and download if it does not exist
        File db = new File(getFilesDir(), ACRONYMS_FILENAME);
        if (db.exists()) {
            loadAcronymsDb(db);
        } else {
            wtfDownloadTask = new WTFDownloadTask(MainActivity.this);
            wtfDownloadTask.execute();
        }
    }

    /**
     * Called to fill action bar
     *
     * @param menu The menu that got instantiated by Android
     * @return always true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    protected void styleDialogue(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.show();

        // Set title divider color
        // from http://blog.supenta.com/2014/07/02/how-to-style-alertdialogs-like-a-pro/
        int titleDividerId = getResources().getIdentifier("titleDivider", "id", "android");
        View titleDivider = dialog.findViewById(titleDividerId);
        if (titleDivider != null)
            titleDivider.setBackgroundColor(0xFF22C819); //XXX FIXME getColor(R.color.colorAccent));
    }

    /**
     * Called when an action bar item was clicked
     *
     * @param item instance of the clicked item
     * @return return value of superclass method
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Get id of activated menu item
        int id = item.getItemId();

        // Find out what menu entry was clicked
        if (id == R.id.action_update) {
            // "Update" item - run download task
            wtfDownloadTask = new WTFDownloadTask(MainActivity.this);
            wtfDownloadTask.execute();
        } else if (id == R.id.action_about) {
            // Show about dialog
            View messageView = getLayoutInflater().inflate(R.layout.dialog_about, null, false);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.mipmap.ic_launcher);
            builder.setTitle(R.string.app_name);
            builder.setView(messageView);
            // Get numbers
            int numAcronyms = acronyms.size();
            int numExpansions = 0;
            for (ArrayList expansions : acronyms.values()) {
                numExpansions += expansions.size();
            }
            // Set version number
            TextView tVersion = (TextView) messageView.findViewById(R.id.tVersion);
            try {
                tVersion.setText("App version: " + getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            // Set acronyms info in about dialog
            TextView tAcronyms = (TextView) messageView.findViewById(R.id.tAcronyms);
            tAcronyms.setText("Acronyms database version " + acronyms_version + "\n\nWTF knows about " + numAcronyms + " acronyms and " + numExpansions + " expansions.");
            if (cats >= 3) {
                TextView tAcronymsSource = (TextView) messageView.findViewById(R.id.tAcronymsSource);
                tAcronymsSource.append("\n\nCat content © 2015 Dominik George, CC-BY-SA 3.0+.");
            }
            builder.create();
            styleDialogue(builder);
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Normalise acronym and return result
     *
     * @param acronym the input acronym
     * @return the normalised acronym
     */
    public String normaliseAcronym(String acronym) {
        // Upper case A-Z first
        acronym = acronym.toUpperCase(Locale.ENGLISH);

        // Upper case with limited matching rules loaded from acronyms database
        for (String original : uppercasables.keySet()) {
            acronym = acronym.replace(original, uppercasables.get(original));
        }

        // Remove dots if acronym contains letter followed by dot
        if (acronym.matches(".*[A-Z]\\..*")) {
            acronym = acronym.replace(".", "");
        }

        return acronym;
    }

    /**
     * Method called to do an actual search on acronyms
     *
     * @throws IOException if acronyms database could not be read
     */
    private void doWTFSearch() throws IOException {
        // Normalise input
        String acronym = normaliseAcronym(eAcronym.getText().toString());

        if ((acronym.equals("MIAU") || acronym.equals("MEOW")) && (cats < 3)) {
            // Increase cat counter if MIAU
            cats++;
            tCats.append("\uD83D\uDE38");

            // Cat content if enough MIAU
            if (cats == 3) {
                final View catView = getLayoutInflater().inflate(R.layout.dialog_eastercat, null, false);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.miau);
                builder.setView(catView);
                builder.create();
                styleDialogue(builder);

                catView.post(new Runnable() {
                    @Override
                    public void run() {
                        // Get image view and size
                        ImageView iCat = (ImageView) catView.findViewById(R.id.iCat);
                        int w = iCat.getMeasuredWidth();

                        // Get scaled image
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeResource(getResources(), R.drawable.eastercat, options);
                        if (options.outWidth > w)
                            options.inSampleSize = (int) Math.ceil((double) options.outWidth / (double) w);
                        options.inJustDecodeBounds = false;
                        iCat.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.eastercat, options));
                    }
                });
            }
        }

        // Empty result textbox
        resultsAdapter.clear();

        // Look up in hash table
        if (acronyms.containsKey(acronym)) {
            // Append all the entries to the result view
            // This could be optimised, but sticking to this in order to keep API level low
            for (String entry : acronyms.get(acronym)) {
                resultsAdapter.add(entry);
            }
        } else {
            resultsAdapter.add("Gee, I don’t know…");
        }

        // Set headline
        tHeading.setText(acronym + " means…");

        // Remove focus from text field and empty it
        eAcronym.clearFocus();
        eAcronym.setText("");

        // hide virtual keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(eAcronym.getWindowToken(), 0);
    }

    /**
     * Load acronyms database into Hashtable
     */
    public void loadAcronymsDb(File db) {
        // Empty Hashtables
        if (acronyms == null) {
            acronyms = new Hashtable<>();
        } else {
            acronyms.clear();
        }
        if (uppercasables == null) {
            uppercasables = new Hashtable<>();
        } else {
            uppercasables.clear();
        }

        // Opportunistically remove old uncompressed acronyms database
        File oldDB = new File(getFilesDir(), "acronyms.db");
        oldDB.delete();

        try {
            // Open acronyms database and initialise reader
            if (db == null) {
                db = new File(getFilesDir(), ACRONYMS_FILENAME);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(db))));

            // Read file line by line into Hashtable
            String line;
            String[] lineData;
            while ((line = r.readLine()) != null) {
                // Check whether line is in acronym entry format…
                if (line.contains("\t")) {
                    // Split on TAB
                    lineData = line.split("\t");

                    // Insert into Hashtable if not existent
                    if (!acronyms.containsKey(lineData[0])) {
                        acronyms.put(lineData[0], new ArrayList<String>());
                    }
                    acronyms.get(lineData[0]).add(lineData[1]);
                    // …or in uppercase definition line format
                } else if (line.startsWith("  ")) {
                    String[] pairs = line.substring(2).split(" ");

                    for (String pair : pairs) {
                        String[] kv = pair.split("/");
                        uppercasables.put(kv[0], kv[1]);
                    }
                } else if (line.startsWith(" @(#)$MirOS: www/.linked/acronyms,v ")) {
                    acronyms_version = line.substring(36, line.length()-6);
                }
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error reading acronyms database", Toast.LENGTH_LONG).show();
        }

        // Link list of known acronyms to text field auto completion
        Object[] sorted = acronyms.keySet().toArray();
        Arrays.sort(sorted);
        ArrayAdapter acronymKeys = new WtfArrayAdapter<>(this, android.R.layout.simple_list_item_1, sorted, this);
        eAcronym.setAdapter(acronymKeys);
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        private static final String TAG = "MyBroadcastReceiver";

        @Override
        public void onReceive ( final Context context, final Intent intent){
            MainActivity.this.loadAcronymsDb(null);
        }
    }

    /**
     * Called when an UI element was clicked
     *
     * @param v calling view
     */
    @Override
    public void onClick(View v) {
        // Find out what UI element was clicked
        if (v.getId() == bSearch.getId()) {
            try {
                // Do acronyms search
                doWTFSearch();
            } catch (Exception e) {
                // Show toast with error if acronyms database could not be searched
                Toast.makeText(this, "Error searching acronyms database", Toast.LENGTH_LONG).show();
            }
        }
    }
}
