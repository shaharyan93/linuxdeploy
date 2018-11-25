package ru.meefik.linuxdeploy;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class RepositoryActivity extends AppCompatActivity {

    class RetrieveIndexTask extends AsyncTask<String, Void, Boolean> {

        private ProgressDialog dialog;
        private Context context;

        RetrieveIndexTask(Context context) {
            this.context = context;
            this.dialog = new ProgressDialog(context);
        }

        @Override
        protected void onPreExecute() {
            dialog.setMessage(context.getString(R.string.loading_message));
            dialog.show();
            profiles.clear();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            // params comes from the execute() call: params[0] is the url.
            try {
                downloadUrl(params[0]);
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            adapter.notifyDataSetChanged();
            if (!success) {
                Toast.makeText(context, R.string.toast_loading_error, Toast.LENGTH_SHORT).show();
            }
        }

        private void downloadUrl(String url) throws IOException {
            BufferedReader reader = null;
            try {
                URL u = new URL(new URL(url), "index.gz");
                reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(u.openStream())));
                String line;
                Map<String, String> map = new HashMap<>();
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (!map.isEmpty()) profiles.add(map);
                        map = new HashMap<>();
                        continue;
                    }
                    if (!line.startsWith("#")) {
                        String[] pair = line.split("=");
                        String key = pair[0];
                        String value = pair[1];
                        map.put(key, value);
                    }
                }
            } finally {
                if (reader != null) reader.close();
            }
        }
    }

    class ImportProfileTask extends AsyncTask<String, Void, Boolean> {

        private ProgressDialog dialog;
        private Context context;
        private String profile;

        ImportProfileTask(Context context) {
            this.context = context;
            this.dialog = new ProgressDialog(context);
        }

        @Override
        protected void onPreExecute() {
            dialog.setMessage(context.getString(R.string.loading_message));
            dialog.show();
        }

        @Override
        protected Boolean doInBackground(String... params) {
            profile = params[1];
            try {
                downloadUrlAndImport(params[0], params[1]);
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            if (success) {
                PrefStore.changeProfile(getApplicationContext(), profile);
                finish();
            } else {
                Toast.makeText(context, R.string.toast_loading_error, Toast.LENGTH_SHORT).show();
            }
        }

        private void downloadUrlAndImport(String url, String profile) throws IOException {
            String conf = PrefStore.getEnvDir(context) + "/config/" + profile + ".conf";
            InputStream in = null;
            OutputStream out = null;
            try {
                URL u = new URL(new URL(url), "config/" + profile + ".conf");
                in = u.openStream();
                out = new FileOutputStream(conf);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                if (in != null) in.close();
                if (out != null) out.close();
            }
        }
    }

    private List<Map<String, String>> profiles = new ArrayList<>();
    private ArrayAdapter adapter;

    private boolean isDonated() {
        return getPackageManager().checkSignatures(getPackageName(), "ru.meefik.donate")
                == PackageManager.SIGNATURE_MATCH;
    }

    private void importDialog(final Map<String, String> profile) {
        final String name = profile.get("PROFILE");
        final String message = getString(R.string.repository_import_message,
                profile.get("DESC"),
                profile.get("SIZE"));
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton(android.R.string.no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.cancel();
                            }
                        });
        if (profile.get("PROTECTED") != null && !isDonated()) {
            dialog.setPositiveButton(R.string.repository_purchase_button,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=ru.meefik.donate")));
                        }
                    });
        } else {
            dialog.setPositiveButton(R.string.repository_import_button,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            importProfile(name);
                        }
                    });
        }
        dialog.show();
    }

    @SuppressLint("RestrictedApi")
    private void changeUrlDialog() {
        final EditText input = new EditText(this);
        input.setText(PrefStore.getRepositoryUrl(this));
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.repository_change_url_title)
                .setView(input, 16, 32, 16, 0)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                String text = input.getText().toString();
                                if (text.isEmpty()) text = getString(R.string.repository_url);
                                PrefStore.setRepositoryUrl(getApplicationContext(), text);
                                retrieveIndex();
                            }
                        }).setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        dialog.cancel();
                    }
                }).show();
    }

    private void retrieveIndex() {
        String url = PrefStore.getRepositoryUrl(this);
        new RetrieveIndexTask(this).execute(url);
    }

    private void importProfile(String name) {
        String url = PrefStore.getRepositoryUrl(this);
        new ImportProfileTask(this).execute(url, name);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PrefStore.setLocale(this);
        setContentView(R.layout.activity_repository);

        // ListView Adapter
        ListView listView = findViewById(R.id.repositoryView);
        adapter = new ArrayAdapter<Map<String, String>>(this,
                R.layout.repository_row, R.id.repo_entry_title, profiles) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView title = view.findViewById(R.id.repo_entry_title);
                TextView subTitle = view.findViewById(R.id.repo_entry_subtitle);
                ImageView icon = view.findViewById(R.id.repo_entry_icon);
                String name = profiles.get(position).get("PROFILE");
                String desc = profiles.get(position).get("DESC");
                String type = profiles.get(position).get("TYPE");
                int iconRes = R.raw.linux;
                switch (type) {
                    case "archlinux":
                        iconRes = R.raw.archlinux;
                        break;
                    case "centos":
                        iconRes = R.raw.centos;
                        break;
                    case "debian":
                        iconRes = R.raw.debian;
                        break;
                    case "fedora":
                        iconRes = R.raw.fedora;
                        break;
                    case "gentoo":
                        iconRes = R.raw.gentoo;
                        break;
                    case "kalilinux":
                        iconRes = R.raw.kalilinux;
                        break;
                    case "slackware":
                        iconRes = R.raw.slackware;
                        break;
                    case "ubuntu":
                        iconRes = R.raw.ubuntu;
                        break;
                }
                InputStream imageStream = view.getResources().openRawResource(iconRes);
                Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
                icon.setImageBitmap(bitmap);
                title.setText(name);
                if (desc != null && !desc.isEmpty()) subTitle.setText(desc);
                else subTitle.setText(getString(R.string.repository_default_description));
                return view;
            }
        };
        listView.setAdapter(adapter);

        // Click listener
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Map profile = (Map) parent.getItemAtPosition(position);
                importDialog(profile);
            }
        });
    }

    @Override
    public void setTheme(int resId) {
        super.setTheme(PrefStore.getTheme(this));
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(R.string.title_activity_repository);
        retrieveIndex();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        PrefStore.setLocale(this);
        getMenuInflater().inflate(R.menu.activity_repository, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                retrieveIndex();
                break;
            case R.id.menu_change_url:
                changeUrlDialog();
                break;
        }
        return false;
    }

}
