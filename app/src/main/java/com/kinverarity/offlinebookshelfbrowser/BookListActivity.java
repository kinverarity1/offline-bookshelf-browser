package com.kinverarity.offlinebookshelfbrowser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.googlecode.jcsv.CSVStrategy;
import com.googlecode.jcsv.reader.CSVEntryParser;
import com.googlecode.jcsv.reader.CSVReader;
import com.googlecode.jcsv.reader.internal.CSVReaderBuilder;

public class BookListActivity extends ListActivity {
    String TAG = "BookListActivity";
    LogHandler logger;
    Intent intent;
    String MESSAGE_TABLE_NAME = "com.kinverarity.offlinebookshelfbrowser.TABLE_NAME";
    InputStreamReader inputStreamReader = null;
    
    int RESULT_TAG_SELECT = 1;
    int RESULT_COLLECTION_SELECT = 2;
    
    int PROGRESS_LOGGED_IN = 3;
    int PROGRESS_SUCCESS = 4;
    int PROGRESS_LOGIN_FAIL = 5;

    Cursor cursor;
    ArrayList<Integer> _ids = new ArrayList<Integer>();
    ArrayList<Integer> toDeleteIds = new ArrayList<Integer>();
    SearchHandler searchHandler;
    BookListAdapter adapter;
    
    SharedPreferences sharedPref;
    SharedPreferences.Editor prefsEdit;
    
    String currentSortOrder;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        String METHOD = ".onCreate()";
        //Log.i(TAG + METHOD, "performance_track onCreate_start ");
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_list);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        logger = new LogHandler(sharedPref);
        logger.log(TAG + METHOD, "Start");
        
        currentSortOrder = sharedPref.getString("sortOrder", "_id");

        // Persistent cookie handler across all URL connections
        CookieHandler.setDefault( new CookieManager( null, CookiePolicy.ACCEPT_ALL ) );

        searchHandler = new SearchHandler(this);
        searchHandler.setIds();
        setTitle(getString(R.string.topbar_allbooks));
        
        //Log.i(TAG + METHOD, "performance_track prior_to_intent_parsing ");
        
        intent = getIntent();
        String action = intent.getAction();
        if (intent.hasExtra("ids")) {
            logger.log(TAG + METHOD, "Intent.hasExtra('ids')");
            searchHandler.setIds(intent.getStringExtra("ids"));
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            logger.log(TAG + METHOD, "Intent.ACTION_VIEW");
            importData();
        } else if (Intent.ACTION_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            logger.log(TAG + METHOD, "Intent.ACTION_SEARCH query=" + query);
            searchHandler.restrictByQuery(query);
            setTitle(query);
            loadList();
        } else if (intent.hasExtra("tagName")) {
            String tag = intent.getStringExtra("tagName");
            logger.log(TAG + METHOD, "Intent has an extra: tagName=" + tag);
            searchHandler.restrictByTag(tag);
            setTitle(tag);
            loadList();
        } else if (intent.hasExtra("collectionName")) {
            String collection = intent.getStringExtra("collectionName");
            logger.log(TAG + METHOD, "Intent has an extra: collectionName=" + collection);
            searchHandler.restrictByCollection(collection);
            setTitle(collection);
            loadList();
        } else if (intent.hasExtra("authorName")) {
            String author = intent.getStringExtra("authorName");
            logger.log(TAG + METHOD, "Intent has an extra: authorName=" + author);
            searchHandler.restrictByAuthor(author);
            setTitle(author);
            loadList();
        } else if (intent.hasExtra("downloadBooks")) {
            logger.log(TAG + METHOD, "Intent has an extra: downloadBooks");
            downloadBooks();
        } else {
            logger.log(TAG + METHOD, "Intent.getAction() = " + intent.getAction() + "... ignoring.");
            loadList();
        }
        //Log.i(TAG + METHOD, "performance_track intent_parse_finish");
        
        if (searchHandler.size() == 1) {
            Intent intent = new Intent(this, BookDetailActivity.class);
            intent.putExtra("_id", searchHandler.getColumnArray("_id").get(0));
            startActivity(intent);
            finish();
        }
        //Log.i(TAG + METHOD, "performance_track onCreate_finish");
    }

    public void onPause () {
        super.onPause();
        _ids = searchHandler.getIds();
    }
    
    public void onResume () {
        super.onResume();
        String METHOD = ".onResume()"; 
        
        if (!(currentSortOrder == sharedPref.getString("sortOrder", "_id"))) {
            startActivity(new Intent(this, BookListActivity.class));
            finish();
        }
        
        //Log.i(TAG + METHOD, "performance_track onResume_finish");
    }
    
    public void loadList() {
        String METHOD = ":loadList(): ";
        logger.log(TAG + METHOD, "start");
        //Log.i(TAG + METHOD, "performance_track loadList_start");
        
        cursor = searchHandler.getCursor();
        
        //Log.i(TAG + METHOD, "performance_track loadList_after_getcursor");
        
        if (cursor.getCount() == 0) {
            SearchHandler testSearchHandler = new SearchHandler(this);
            testSearchHandler.setIds();
            if (testSearchHandler.getIds().size() == 0) {
                Intent in = new Intent(this, LoginActivity.class);
                startActivity(in);
            } else {
                
            }
        }
        
        //Log.i(TAG + METHOD, "performance_track loadList_create_adapter_start");
        
        adapter = new BookListAdapter(this, new String[] {"title", "author2", "date"});
        
        //Log.i(TAG + METHOD, "performance_track loadList_created_adapter");
        
        getListView().setFastScrollEnabled(true);
        
        setListAdapter(adapter);
        
        //Log.i(TAG + METHOD, "performance_track loadList_finish");
    }
    
    public void downloadBooks() {
        String METHOD = ":downloadBooks(): ";
        logger.log(TAG + METHOD, "start");
        new LTLoginDownload().execute(null, null, null);
    }

    @SuppressWarnings("unchecked")
    public void importData() {
        String METHOD = ":importData(): ";
        logger.log(TAG + METHOD, "start");
        
        Uri uri = intent.getData();
//        logger.log(TAG, "Intent contains uri=" + uri);

        // Date presentTime = Calendar.getInstance().getTime();
        // SimpleDateFormat dateFormatter = new SimpleDateFormat(
        // "yyyyMMddhhmmss");
        // String newTableName = "booksFrom" +
        // dateFormatter.format(presentTime);
        // logger.log(TAG, "Intended table name=" + newTableName);

//        logger.log(TAG, "Opening InputStreamReader for " + uri + "...");
        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }

//        logger.log(TAG, "Creating CSVReader...");
        try {
            inputStreamReader = new InputStreamReader(inputStream, "utf-16");
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        }
        
        CSVReader<String[]> csvReader = new CSVReaderBuilder<String[]>(
                inputStreamReader)
                .strategy(new CSVStrategy('\t', '\b', '#', true, true))
                .entryParser(new EntryParser()).build();

        logger.log(TAG, "Reading csvData...");
        List<String[]> csvData = null;
        try {
            csvData = csvReader.readAll();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.log(TAG, "Successfully read csvData");

        ImportBooksTask task = new ImportBooksTask();
        task.execute(csvData);
    }

    public class EntryParser implements CSVEntryParser<String[]> {
        public String[] parseEntry(String... data) {
            return data;
        }
    }

    private class ImportBooksTask extends
            AsyncTask<List<String[]>, Void, String> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            String METHOD = ":ImportBooksTask:onPreExecute(): ";
            logger.log(TAG + METHOD, "start");
            
            dialog = new ProgressDialog(BookListActivity.this);
            dialog.setTitle("Importing books...");
            dialog.setMessage("This could take a few minutes.");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }

        @SuppressWarnings("static-access")
        @Override
        protected String doInBackground(List<String[]>... csvDatas) {
            String METHOD = ":ImportBooksTask:doInBackground(...): ";
            logger.log(TAG + METHOD, "start");
            
            List<String[]> csvData = csvDatas[0];
            String newTableName = "books";
            logger.log(TAG, "Opening " + newTableName + " in internal database...");
            DbHelperNew dbHelper = new DbHelperNew(getApplicationContext());
            dbHelper.open();
            dialog.setMax(csvData.size());
            dbHelper.Db.beginTransaction();
            try {
                for (int i = 0; i < csvData.size(); i++) {
                    String[] csvRow = csvData.get(i);
                    String[] csvRowShort = new String[csvRow.length - 1];
                    for (int j = 0; j < (csvRowShort.length); j++) {
                        csvRowShort[j] = csvRow[j];
                    }
                    dbHelper.addRow(csvRowShort);
                    dbHelper.Db.yieldIfContendedSafely();
                    dialog.setProgress(i);
                }
                
                if (toDeleteIds.size() > 0) {
                    String deleteSQL = "_id IN (";
                    for (Integer deleteID : toDeleteIds) {
                        deleteSQL += " " + deleteID + " ,";
                    }
                    deleteSQL = deleteSQL.substring(0, deleteSQL.length() - 2) + ")";
                    dbHelper.Db.delete(dbHelper.TABLE, deleteSQL, null);
                }
                
                dbHelper.Db.setTransactionSuccessful();
            } finally {
                dbHelper.Db.endTransaction();
            }
            dbHelper.close();
            
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            String METHOD = ":ImportBooksTask:onPostExecute(" + result + "): ";
            logger.log(TAG + METHOD, "start");
            
            //dialog.dismiss();
            Intent i = new Intent(getApplicationContext(), BookListActivity.class);
            finish();
            startActivity(i);
        }
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position,
            long id) {
        String METHOD = ":onListItemClick(position=" + position + "): ";
        logger.log(TAG + METHOD, "start");
        
        super.onListItemClick(listView, view, position, id);
        cursor.moveToPosition(position);
        String _id = cursor.getString(cursor.getColumnIndex("_id"));
        Intent intent = new Intent(this, BookDetailActivity.class);
        intent.putExtra("_id", _id);
        startActivity(intent);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Place an action bar item for searching.
        getMenuInflater().inflate(R.menu.options, menu);
//        // MenuItem item = menu.add("Search");
//        MenuItem item = menu.findItem(R.id.menuSearch);
//        // item.setIcon(android.R.drawable.ic_menu_search);
//        // item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
//        // SearchView sv = new SearchView(this);
//        // sv.setOnQueryTextListener(this);
//        // item.setActionView(sv);
//        //
        return true;
    }
    
    public boolean launchedAsQuery () {
        intent = getIntent();
        if (intent.hasExtra("ids")) return true;
        else if (intent.hasExtra("tagName")) return true;
        else if (intent.hasExtra("collectionName")) return true;
        else if (intent.hasExtra("authorName")) return true;
        return false;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
//        menu.findItem(R.id.menuShowAllBooks).setVisible(launchedAsQuery());
        return true;
    }

    @SuppressWarnings("unused")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menuPreferences:
            goToPreferences();
            return true;
        case R.id.menuSearch:
            this.onSearchRequested();
            return true;
        case R.id.menuDelete:
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("Delete books")
                    .setMessage(
                            "Are you sure you want to delete all your books?\n\n(This is only for data imported into LibraryThing Browser on your device, and cannot affect your LibraryThing account).")
                    .setPositiveButton("Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    String tableName = "books";
                                    DbHelperNew dbHelper = new DbHelperNew(
                                            getApplicationContext());
                                    dbHelper.delete();
                                    loadList();
                                }

                            }).setNegativeButton("No", null).show();
            return true;
        case R.id.menuShowAllBooks:
            startActivity(new Intent(this, BookListActivity.class));
            return true;
        case R.id.menuTags:
            startActivityWithIds(new Intent(this, TagListActivity.class));
            return true;
        case R.id.menuCollections:
            startActivityWithIds(new Intent(this, CollectionListActivity.class));
            return true;
        case R.id.menuAuthors:
            startActivityWithIds(new Intent(this, AuthorListActivity.class));
            return true;
//        case R.id.menuSort:
//            final CharSequence[] items = {"title", "author1", "author2"};
//
//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle("Sort by");
//            builder.setItems(items, new DialogInterface.OnClickListener() {
//                public void onClick(DialogInterface dialog, int item) {
//                    orderByColumn = items[item].toString();
//                    loadList();
//                    dialog.dismiss();
//                }
//            });
//            AlertDialog alert = builder.create();
//            alert.show();
//            return true;
        case R.id.menuScanBarcode:
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.initiateScan();
            return true;
        case R.id.menuImport:
            downloadBooks();
            return true;
        case R.id.menuReviews:
            startActivityWithIds(new Intent(this, ReviewListActivity.class));
            return true;
        case R.id.menuComments:
            startActivityWithIds(new Intent(this, CommentListActivity.class));
            return true;
        default:
            return false;
        }
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
          // handle scan result
            String potentialISBN = scanResult.getContents();
            String searchString = "";
            if (potentialISBN.length() > 10) {
                searchString = potentialISBN.subSequence(potentialISBN.length() - 10, potentialISBN.length() - 1).toString();
            } else {
                searchString = potentialISBN;
            }
            if (searchString.length() == 10) {
                searchString = searchString.subSequence(0, 9).toString();
            }
//            Toast.makeText(this, searchString, Toast.LENGTH_LONG).show();
            Intent searchIntent = new Intent(this, BookListActivity.class);
            searchIntent.setAction(Intent.ACTION_SEARCH);
            searchIntent.putExtra(SearchManager.QUERY, searchString);
            startActivity(searchIntent);
        }
        // else continue with any other code you need in the method
      }
    
    public void startActivityWithIds (Intent intent) {
        intent.putExtra("ids", searchHandler.getString());
        startActivity(intent);
    }

    public void importBooksFromDownload(String downloadedContent) {
        InputStream is = new ByteArrayInputStream(downloadedContent.getBytes());
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        CSVReader<String[]> csvReader = new CSVReaderBuilder<String[]>(
                br)
                .strategy(new CSVStrategy('\t', '\b', '#', true, true))
                .entryParser(new EntryParser()).build();

        logger.log(TAG, "Reading downloadedContent...");
        List<String[]> csvData = null;
        try {
            csvData = csvReader.readAll();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.log(TAG, "Successfully read downloadedContent");
        
        toDeleteIds = searchHandler.getIds();

        ImportBooksTask task = new ImportBooksTask();
        task.execute(csvData);
        
        prefsEdit = sharedPref.edit();
        String s = SimpleDateFormat.getDateTimeInstance().format(new Date());
        prefsEdit.putString("last_download_summary", "Most recent: " + s);
        prefsEdit.commit();
    }
    
    private class LTLoginDownload extends AsyncTask<Boolean, Integer, String> {
        String result;        
        ProgressDialog dialog;

        
        @Override
        protected void onPreExecute() {
            String METHOD = ":LTLoginDownload:onPreExecute(): ";
            logger.log(TAG + METHOD, "start");

            dialog = new ProgressDialog(BookListActivity.this);
            dialog.setTitle("Downloading library...");
            dialog.setMessage("Logging in...");
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
        
        protected void onProgressUpdate(Integer... progUpdate) {
            String METHOD = ".LTLoginDownload.onProgressUpdate()";
            
            if (progUpdate[0] == PROGRESS_LOGGED_IN){  
               dialog.setMessage("Successfully logged in. Now downloading your library.");
               if (!sharedPref.getBoolean("lt_remember_credentials", false)) {
                   prefsEdit = sharedPref.edit();
                   prefsEdit.putString("lt_username", "");
                   prefsEdit.putString("lt_password", "");
                   prefsEdit.commit();
               }
           } else if (progUpdate[0] == PROGRESS_SUCCESS) {
               dialog.setMessage("Successful! Let's import your books.");
           } else if (progUpdate[0] == PROGRESS_LOGIN_FAIL) {
               logger.log(TAG + METHOD, "Login failed.");
               dialog.setMessage("Login failed.");
               dialog.dismiss();
               Intent in = new Intent(getApplicationContext(), LoginActivity.class);
               startActivity(in);
           }
        }
        
        protected String doInBackground(Boolean... bools) {
            String METHOD = ".LTLoginDownload.doInBackground()";
            logger.log(TAG + METHOD, "start");
            
            String loginResponseBody = "";

            HttpURLConnection urlConnection = null;
            try {
                String loginPostContent = URLEncoder.encode("formusername", "UTF-8") + "="
                        + URLEncoder.encode(sharedPref.getString("lt_username", ""), "UTF-8") + "&"
                        + URLEncoder.encode("formpassword", "UTF-8") + "="
                        + URLEncoder.encode(sharedPref.getString("lt_password", ""), "UTF-8") + "&"
                        + URLEncoder.encode("index_signin_already", "UTF-8") + "="
                        + URLEncoder.encode("Sign in", "UTF-8");

                URL url = new URL("https://www.librarything.com/enter/start");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                urlConnection.setChunkedStreamingMode(0);

                //Send request
                DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
                wr.writeBytes(loginPostContent);
                wr.flush();
                wr.close();

                int responseCode = urlConnection.getResponseCode();

                // follow redirect after successful login
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                    urlConnection.disconnect();
                    url = new URL(urlConnection.getHeaderField("Location"));
                    urlConnection = (HttpURLConnection) url.openConnection();
                }

                //Get Response
                final String type = urlConnection.getHeaderField("Content-Type");
                final String charset = type.substring(type.indexOf("charset")+8);

                InputStream is = urlConnection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, charset));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                loginResponseBody = response.toString();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }

            logger.log(TAG + METHOD, "loginResponseBody=" + loginResponseBody);
            if (!loginResponseBody.contains("Home | LibraryThing")) {
                logger.log(TAG + METHOD, "Login failed.");
                this.publishProgress(PROGRESS_LOGIN_FAIL);
                this.cancel(true);
            } else {
                logger.log(TAG + METHOD, "Login succeeded.");
                this.publishProgress(PROGRESS_LOGGED_IN);
            }

            String downloadResponseBody = "";
            try {
                URL url = new URL("http://www.librarything.com/export-tab");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setReadTimeout(10000);

                String type = urlConnection.getHeaderField("Content-Type");
                String charset = type.substring(type.indexOf("charset")+8);

                InputStream is = urlConnection.getInputStream();
                BufferedReader rd = new BufferedReader(new InputStreamReader(is, charset));
                String line;
                StringBuilder response = new StringBuilder();
                while((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                rd.close();
                downloadResponseBody = response.toString();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }

            logger.log(TAG + METHOD, "downloadResponseBody=" + downloadResponseBody);
            result = downloadResponseBody;
            
            this.publishProgress(PROGRESS_SUCCESS);
            return "";
        }

        private String readStream(InputStream in) {
            StringBuilder sb = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                String nextLine = "";
                while ((nextLine = reader.readLine()) != null) {
                    sb.append(nextLine);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return sb.toString();
        }

        protected void onPostExecute(String r) {
            String METHOD = ".LTLoginDownload.onPostExecute()";
            logger.log(TAG + METHOD, "start");
            if (result.length() > 0) {
                logger.log(TAG + METHOD, "importing books");
                importBooksFromDownload(result);
            } else {
                logger.log(TAG + METHOD, "the download was empty");
            }
        }

    }
    
    public void goToPreferences() {
        String METHOD = ".goToPreferences()";
        logger.log(TAG + METHOD, "opening application settings");
        Intent i = new Intent(this, PreferencesActivity.class);
        startActivity(i);
    }
    
    public class BookListAdapter extends BaseAdapter {

        private Context context;
        LayoutInflater inflater;
        private ArrayList<ArrayList<String>> columns = new ArrayList<ArrayList<String>>();
        
        public BookListAdapter (Context context, String[] columnNames) {
            String METHOD = ".BookListAdapter constructor()";
            this.context = context;
            inflater = LayoutInflater.from(context);
            //Log.i(TAG + METHOD, "performance_track BookListAdapter_tag1");
            
            for (int i = 0; i < columnNames.length; i++) {
                columns.add(searchHandler.getColumnArray(columnNames[i]));
            }
            //Log.i(TAG + METHOD, "performance_track BookListAdapter_tag2");
        }
        
        public int getCount() {
            return columns.get(0).size();
        }

        public Object getItem(int position) {
            return null;
        }

        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.book_list_item, null);
            }
            TextView titleView = (TextView) convertView.findViewById(R.id.book_list_item_title);
            TextView authorView = (TextView) convertView.findViewById(R.id.book_list_item_subtitle);
            TextView pubDateView = (TextView) convertView.findViewById(R.id.book_list_item_rhsdate);
            titleView.setText(FormatText.asHtml(columns.get(0).get(position)));
            authorView.setText(FormatText.asHtml(columns.get(1).get(position)));
            pubDateView.setText(FormatText.asHtml(columns.get(2).get(position)));
            return convertView;
        }
    }

//    public class BookListCursorAdapter extends CursorAdapter {
//        LayoutInflater inflater;
//        @SuppressWarnings("deprecation")
//        public BookListCursorAdapter(Context context, Cursor c) {
//            super(context, c);
//            inflater = LayoutInflater.from(context);
//        }
//
//        @Override
//        public void bindView(View view, Context context, Cursor cursor) {
//            TextView titleTV = (TextView) view
//                    .findViewById(R.id.book_list_item_title);
//            TextView subTitleTV = (TextView) view
//                    .findViewById(R.id.book_list_item_subtitle);
//            titleTV.setText(FormatText.asHtml(cursor.getString(cursor.getColumnIndex("title"))));
//            subTitleTV.setText(FormatText.asHtml(cursor.getString(cursor
//                    .getColumnIndex("author2"))));
//
//        }
//
//        @Override
//        public View newView(Context context, Cursor cursor, ViewGroup parent) {
//            return inflater.inflate(R.layout.book_list_item, parent, false);
//        }
//    }
}
