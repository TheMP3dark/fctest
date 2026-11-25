package com.firstcry.quickcart;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15,15,15);
    private static final int PANEL = Color.rgb(27,27,27);
    private static final int RED = Color.rgb(227,38,54);
    private static final int WHITE = Color.rgb(245,245,245);
    private static final int MUTED = Color.rgb(160,160,160);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private final ArrayList<Account> accounts = new ArrayList<>();
    private Spinner accountSpinner;
    private TextView accountHint, resultView;
    private EditText idsInput;
    private Button addButton, addAllButton;
    private int selectedIndex = -1;

    static class Account {
        String name;
        String curl;
        Account(String n, String c) { name=n; curl=c; }
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject(); o.put("name", name); o.put("curl", curl); return o;
        }
        static Account fromJson(JSONObject o) {
            return new Account(o.optString("name"), o.optString("curl"));
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9,9,9));
        getWindow().setNavigationBarColor(Color.rgb(9,9,9));
        prefs = getSharedPreferences("fc_accounts", MODE_PRIVATE);
        loadAccounts();
        loadBookmarks();
        buildUi();
    }

    private void loadAccounts() {
        accounts.clear();
        String raw = prefs.getString("accounts", "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i=0;i<a.length();i++) accounts.add(Account.fromJson(a.getJSONObject(i)));
        } catch(Exception ignored) {}
        selectedIndex = prefs.getInt("selected", accounts.isEmpty() ? -1 : 0);
        if (selectedIndex >= accounts.size()) selectedIndex = accounts.isEmpty() ? -1 : 0;
    }

    private void saveAccounts() {
        JSONArray a = new JSONArray();
        try { for(Account ac:accounts) a.put(ac.toJson()); } catch(Exception ignored) {}
        prefs.edit().putString("accounts", a.toString()).putInt("selected", selectedIndex).apply();
        refreshSpinner();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getStableInsetTop(), 0, insets.getStableInsetBottom());
            return insets;
        });
        setContentView(root);

        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(22));
        page.setBackgroundColor(BG);
        homeScroll.addView(page);
        root.addView(homeScroll, new FrameLayout.LayoutParams(-1, -1));

        page.addView(buildHeader("FirstCry Quick Cart"));
        addSpace(page, 22);

        addLabel(page, "SELECT ACCOUNT");
        accountSpinner = new Spinner(this);
        styleSpinner(accountSpinner);
        page.addView(accountSpinner, matchWrap());
        accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedIndex = accounts.isEmpty() ? -1 : pos;
                prefs.edit().putInt("selected", selectedIndex).apply();
                updateAccountHint();
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        accountHint = new TextView(this); accountHint.setTextColor(MUTED); accountHint.setTextSize(11);
        page.addView(accountHint);
        LinearLayout accountActions = new LinearLayout(this); accountActions.setOrientation(LinearLayout.HORIZONTAL);
        Button addAccount = button("＋ ADD ACCOUNT", true);
        Button editAccount = button("EDIT", false);
        Button deleteAccount = button("DELETE", false);
        accountActions.addView(addAccount, new LinearLayout.LayoutParams(0, dp(44), 1));
        addGap(accountActions, 6);
        accountActions.addView(editAccount, new LinearLayout.LayoutParams(0, dp(44), 0.65f));
        addGap(accountActions, 6);
        accountActions.addView(deleteAccount, new LinearLayout.LayoutParams(0, dp(44), 0.7f));
        page.addView(accountActions);
        addAccount.setOnClickListener(v -> showAccountDialog(-1));
        editAccount.setOnClickListener(v -> { if(selectedIndex>=0) showAccountDialog(selectedIndex); else toast("Add an account first."); });
        deleteAccount.setOnClickListener(v -> confirmDelete());

        addSpace(page, 25);
        addLabel(page, "PRODUCT IDs");
        TextView sub = new TextView(this); sub.setText("One per line, comma-separated or space-separated"); sub.setTextColor(MUTED); sub.setTextSize(11);
        page.addView(sub);
        idsInput = new EditText(this);
        idsInput.setHint("24961441\n24961454\n24476798");
        idsInput.setHintTextColor(Color.GRAY);
        idsInput.setTextColor(WHITE);
        idsInput.setTextSize(15);
        idsInput.setGravity(Gravity.TOP | Gravity.LEFT);
        idsInput.setMinLines(5); idsInput.setMaxLines(9);
        idsInput.setPadding(dp(12),dp(12),dp(12),dp(12));
        idsInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        styleField(idsInput);
        LinearLayout.LayoutParams ip = matchWrap(); ip.topMargin=dp(7); page.addView(idsInput, ip);

        addButton = button("ADD TO CART", true);
        LinearLayout.LayoutParams bp = matchWrap(); bp.topMargin=dp(12); page.addView(addButton,bp);
        addButton.setOnClickListener(v -> startAdd());

        addAllButton = button("ADD TO ALL ACCOUNT CART", false);
        LinearLayout.LayoutParams allBp = matchWrap(); allBp.topMargin=dp(8);
        page.addView(addAllButton, allBp);
        addAllButton.setOnClickListener(v -> confirmAddToAll());

        addSpace(page, 18);
        addLabel(page, "RESULTS");
        resultView = new TextView(this);
        resultView.setText("Ready. Select an account and enter product IDs.");
        resultView.setTextColor(MUTED); resultView.setTextSize(12);
        resultView.setPadding(dp(12),dp(12),dp(12),dp(12));
        resultView.setBackground(round(PANEL,10,Color.rgb(48,48,48)));
        page.addView(resultView, matchWrap());

        addSpace(page, 16);
        TextView note = new TextView(this);
        note.setText("Your saved cURL contains an ftk session token. It is stored in this app's private local storage. Do not share it. Tokens may expire; replace the cURL when needed.");
        note.setTextColor(MUTED); note.setTextSize(11);
        page.addView(note);
        refreshSpinner();
        buildBookmarksPage();
        buildDrawer();
        showPage(false);
    }

    // ================= Bookmarks + burger menu =================
    private final ArrayList<String> bookmarks = new ArrayList<>();
    private final LinkedHashSet<String> bookmarkChecked = new LinkedHashSet<>();
    private FrameLayout root;
    private ScrollView homeScroll, bookmarksScroll;
    private LinearLayout bookmarkList, drawer;
    private TextView bookmarkCount, drawerCartItem, drawerBookmarksItem;
    private EditText bookmarkInput;
    private View scrim;
    private boolean drawerOpen = false, onBookmarks = false;

    private void loadBookmarks() {
        bookmarks.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString("bookmarks", "[]"));
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i, "");
                if (!s.isEmpty() && !bookmarks.contains(s)) bookmarks.add(s);
            }
        } catch (Exception ignored) {}
    }

    private void saveBookmarks() {
        JSONArray a = new JSONArray();
        for (String s : bookmarks) a.put(s);
        prefs.edit().putString("bookmarks", a.toString()).apply();
        refreshBookmarkList();
    }

    private LinearLayout buildHeader(String text) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout burger = new LinearLayout(this);
        burger.setOrientation(LinearLayout.VERTICAL);
        burger.setGravity(Gravity.CENTER);
        burger.setClickable(true);
        for (int i = 0; i < 3; i++) {
            View bar = new View(this);
            bar.setBackground(round(WHITE, 1, WHITE));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(22), dp(2));
            if (i > 0) bp.topMargin = dp(5);
            burger.addView(bar, bp);
        }
        burger.setOnClickListener(v -> { hideKeyboard(); if (drawerOpen) closeDrawer(); else openDrawer(); });
        header.addView(burger, new LinearLayout.LayoutParams(dp(44), dp(44)));
        addGap(header, 6);

        TextView dot = new TextView(this); dot.setText("●"); dot.setTextColor(RED); dot.setTextSize(19);
        header.addView(dot);
        TextView title = new TextView(this); title.setText("  " + text); title.setTextColor(WHITE);
        title.setTextSize(22); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        return header;
    }

    private void buildBookmarksPage() {
        bookmarksScroll = new ScrollView(this);
        bookmarksScroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(22), dp(18), dp(22));
        page.setBackgroundColor(BG);
        bookmarksScroll.addView(page);
        root.addView(bookmarksScroll, new FrameLayout.LayoutParams(-1, -1));
        bookmarksScroll.setVisibility(View.GONE);

        page.addView(buildHeader("Bookmarks"));
        addSpace(page, 22);

        addLabel(page, "ADD PRODUCT IDs");
        TextView sub = new TextView(this); sub.setText("One per line, comma-separated or space-separated"); sub.setTextColor(MUTED); sub.setTextSize(11);
        page.addView(sub);
        bookmarkInput = new EditText(this);
        bookmarkInput.setHint("24961441\n24961454");
        bookmarkInput.setHintTextColor(Color.GRAY);
        bookmarkInput.setTextColor(WHITE);
        bookmarkInput.setTextSize(15);
        bookmarkInput.setGravity(Gravity.TOP | Gravity.LEFT);
        bookmarkInput.setMinLines(3); bookmarkInput.setMaxLines(6);
        bookmarkInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        styleField(bookmarkInput);
        LinearLayout.LayoutParams ip = matchWrap(); ip.topMargin = dp(7); page.addView(bookmarkInput, ip);

        Button save = button("＋ SAVE TO BOOKMARKS", true);
        LinearLayout.LayoutParams sp = matchWrap(); sp.topMargin = dp(12); page.addView(save, sp);
        save.setOnClickListener(v -> saveBookmarkInput());

        addSpace(page, 25);
        bookmarkCount = new TextView(this);
        bookmarkCount.setTextColor(MUTED); bookmarkCount.setTextSize(11); bookmarkCount.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams cp = matchWrap(); cp.bottomMargin = dp(6);
        page.addView(bookmarkCount, cp);

        LinearLayout selRow = new LinearLayout(this);
        Button selAll = button("SELECT ALL", false);
        Button selNone = button("CLEAR", false);
        selRow.addView(selAll, new LinearLayout.LayoutParams(0, dp(40), 1));
        addGap(selRow, 6);
        selRow.addView(selNone, new LinearLayout.LayoutParams(0, dp(40), 1));
        page.addView(selRow);
        selAll.setOnClickListener(v -> { bookmarkChecked.addAll(bookmarks); refreshBookmarkList(); });
        selNone.setOnClickListener(v -> { bookmarkChecked.clear(); refreshBookmarkList(); });

        bookmarkList = new LinearLayout(this);
        bookmarkList.setOrientation(LinearLayout.VERTICAL);
        bookmarkList.setBackground(round(PANEL, 10, Color.rgb(48, 48, 48)));
        LinearLayout.LayoutParams lp = matchWrap(); lp.topMargin = dp(8);
        page.addView(bookmarkList, lp);

        Button use = button("USE SELECTED IN CART", true);
        LinearLayout.LayoutParams up = matchWrap(); up.topMargin = dp(12);
        page.addView(use, up);
        use.setOnClickListener(v -> useSelectedBookmarks());

        refreshBookmarkList();
    }

    private void saveBookmarkInput() {
        Matcher m = Pattern.compile("\\d+").matcher(bookmarkInput.getText().toString());
        int added = 0, dup = 0;
        while (m.find()) {
            String id = m.group();
            if (bookmarks.contains(id)) dup++; else { bookmarks.add(id); added++; }
        }
        if (added == 0 && dup == 0) { toast("Enter at least one numeric product ID."); return; }
        if (added > 0) saveBookmarks();
        bookmarkInput.setText("");
        hideKeyboard();
        toast(added + " saved" + (dup > 0 ? " · " + dup + " already saved" : ""));
    }

    private void refreshBookmarkList() {
        if (bookmarkList == null) return;
        bookmarkChecked.retainAll(bookmarks);
        bookmarkList.removeAllViews();
        bookmarkCount.setText("SAVED BOOKMARKS (" + bookmarks.size() + ")");
        if (drawerBookmarksItem != null) drawerBookmarksItem.setText("Bookmarks  (" + bookmarks.size() + ")");
        if (bookmarks.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("No bookmarks yet. Add product IDs above.");
            e.setTextColor(MUTED); e.setTextSize(12);
            e.setPadding(dp(12), dp(14), dp(12), dp(14));
            bookmarkList.addView(e);
            return;
        }
        for (final String id : bookmarks) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), 0, dp(2), 0);

            CheckBox cb = new CheckBox(this);
            cb.setText(id); cb.setTextColor(WHITE); cb.setTextSize(15);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(RED));
            cb.setChecked(bookmarkChecked.contains(id));
            cb.setOnCheckedChangeListener((b, c) -> { if (c) bookmarkChecked.add(id); else bookmarkChecked.remove(id); });
            row.addView(cb, new LinearLayout.LayoutParams(0, dp(48), 1));

            TextView rm = new TextView(this);
            rm.setText("✕"); rm.setTextColor(MUTED); rm.setTextSize(18); rm.setGravity(Gravity.CENTER);
            rm.setClickable(true);
            rm.setOnClickListener(v -> { bookmarks.remove(id); bookmarkChecked.remove(id); saveBookmarks(); });
            row.addView(rm, new LinearLayout.LayoutParams(dp(48), dp(48)));
            bookmarkList.addView(row);

            View line = new View(this);
            line.setBackgroundColor(Color.rgb(40, 40, 40));
            bookmarkList.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void useSelectedBookmarks() {
        if (bookmarkChecked.isEmpty()) { toast("Tick at least one bookmark."); return; }
        StringBuilder sb = new StringBuilder(idsInput.getText().toString().trim());
        int n = 0;
        for (String id : bookmarks) {
            if (bookmarkChecked.contains(id)) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(id); n++;
            }
        }
        idsInput.setText(sb.toString());
        idsInput.setSelection(idsInput.length());
        showPage(false);
        toast(n + " product ID(s) added to the Cart page.");
    }

    private void buildDrawer() {
        final int w = dp(270);
        scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(150, 0, 0, 0));
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closeDrawer());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(Color.rgb(20, 20, 20));
        drawer.setPadding(dp(14), dp(26), dp(14), dp(18));
        drawer.setClickable(true);
        TextView menu = new TextView(this);
        menu.setText("MENU"); menu.setTextColor(MUTED); menu.setTextSize(11); menu.setTypeface(null, Typeface.BOLD);
        menu.setPadding(dp(14), 0, 0, dp(10));
        drawer.addView(menu);
        drawerCartItem = drawerItem("Cart");
        drawerCartItem.setOnClickListener(v -> { closeDrawer(); showPage(false); });
        drawerBookmarksItem = drawerItem("Bookmarks  (" + bookmarks.size() + ")");
        drawerBookmarksItem.setOnClickListener(v -> { closeDrawer(); showPage(true); });
        root.addView(drawer, new FrameLayout.LayoutParams(w, -1));
        drawer.setTranslationX(-w);
    }

    private TextView drawerItem(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(WHITE); t.setTextSize(16);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setClickable(true);
        LinearLayout.LayoutParams p = matchWrap(); p.bottomMargin = dp(4);
        drawer.addView(t, p);
        return t;
    }

    private void openDrawer() {
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(180);
        drawer.animate().translationX(0).setDuration(180);
    }

    private void closeDrawer() {
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(180).withEndAction(() -> { if (!drawerOpen) scrim.setVisibility(View.GONE); });
        drawer.animate().translationX(-dp(270)).setDuration(180);
    }

    private void showPage(boolean bookmarksPage) {
        onBookmarks = bookmarksPage;
        homeScroll.setVisibility(bookmarksPage ? View.GONE : View.VISIBLE);
        bookmarksScroll.setVisibility(bookmarksPage ? View.VISIBLE : View.GONE);
        drawerCartItem.setBackground(bookmarksPage ? null : round(PANEL, 8, RED));
        drawerBookmarksItem.setBackground(bookmarksPage ? round(PANEL, 8, RED) : null);
        hideKeyboard();
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    @Override public void onBackPressed() {
        if (drawerOpen) closeDrawer();
        else if (onBookmarks) showPage(false);
        else super.onBackPressed();
    }


    private void refreshSpinner() {
        if(accountSpinner==null) return;
        ArrayList<String> labels = new ArrayList<>();
        if(accounts.isEmpty()) labels.add("No accounts saved");
        else for(Account a:accounts) labels.add(a.name);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int p, View c, ViewGroup parent) {
                TextView t=(TextView)super.getView(p,c,parent); t.setTextColor(WHITE); t.setTextSize(15); return t;
            }
            @Override public View getDropDownView(int p, View c, ViewGroup parent) {
                TextView t=(TextView)super.getDropDownView(p,c,parent); t.setTextColor(WHITE); t.setBackgroundColor(PANEL); t.setPadding(dp(12),dp(12),dp(12),dp(12)); return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        accountSpinner.setAdapter(adapter);
        if(!accounts.isEmpty()) accountSpinner.setSelection(Math.max(0,Math.min(selectedIndex,accounts.size()-1)));
        updateAccountHint();
    }

    private void updateAccountHint() {
        if(accountHint==null) return;
        if(selectedIndex>=0 && selectedIndex<accounts.size()) accountHint.setText("Saved locally · "+accounts.get(selectedIndex).name);
        else accountHint.setText("Add an account and paste its SaveProductCart cURL.");
    }

    private void showAccountDialog(int index) {
        boolean editing=index>=0;
        Account old=editing?accounts.get(index):null;
        LinearLayout box=new LinearLayout(this); box.setPadding(dp(18),dp(8),dp(18),dp(2)); box.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("Account name (e.g. FC1 – Paru)");
        EditText curl=new EditText(this); curl.setHint("Paste complete SaveProductCart cURL here"); curl.setMinLines(7); curl.setMaxLines(12); curl.setGravity(Gravity.TOP);
        name.setTextColor(WHITE);name.setHintTextColor(MUTED);curl.setTextColor(WHITE);curl.setHintTextColor(MUTED);
        styleField(name);styleField(curl);
        if(editing){name.setText(old.name);curl.setText(old.curl);}
        box.addView(name,matchWrap()); addSpace(box,8); box.addView(curl,matchWrap());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(editing?"Edit account":"Add FirstCry account")
            .setView(box).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim(), c=curl.getText().toString().trim();
            if(n.isEmpty()){name.setError("Enter an account name");return;}
            try{parseCurl(c);}catch(Exception e){curl.setError(e.getMessage());return;}
            if(editing){accounts.set(index,new Account(n,c));selectedIndex=index;}
            else{accounts.add(new Account(n,c));selectedIndex=accounts.size()-1;}
            saveAccounts();dialog.dismiss();toast("Account saved.");
        }));
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(round(PANEL,14,Color.DKGRAY));
    }

    private void confirmDelete() {
        if(selectedIndex<0||selectedIndex>=accounts.size()){toast("No account selected.");return;}
        String n=accounts.get(selectedIndex).name;
        new AlertDialog.Builder(this).setTitle("Delete account?")
            .setMessage("Remove "+n+" and its saved cURL from this app?")
            .setNegativeButton("CANCEL",null).setPositiveButton("DELETE",(d,w)->{
                accounts.remove(selectedIndex);selectedIndex=accounts.isEmpty()?-1:Math.min(selectedIndex,accounts.size()-1);saveAccounts();toast("Account deleted.");
            }).show();
    }

    private void startAdd() {
        if(selectedIndex<0||selectedIndex>=accounts.size()){toast("Select or add an account first.");return;}
        String raw=idsInput.getText().toString();
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        Matcher m=Pattern.compile("\\d+").matcher(raw); while(m.find()) ids.add(m.group());
        if(ids.isEmpty()){toast("Enter at least one numeric product ID.");return;}
        final Account account=accounts.get(selectedIndex);
        final CurlRequest request;
        try{request=parseCurl(account.curl);}catch(Exception e){toast("Saved cURL needs updating: "+e.getMessage());return;}
        addButton.setEnabled(false);addAllButton.setEnabled(false);addButton.setText("ADDING…");
        resultView.setText("Sending "+ids.size()+" product(s) using "+account.name+"…");
        executor.execute(()->{
            ArrayList<String> results=new ArrayList<>();int ok=0;
            for(String id:ids){
                try{
                    String result=addOne(id,request);
                    results.add("✓  "+id+"  "+result);ok++;
                }catch(Exception e){results.add("✕  "+id+"  "+e.getMessage());}
                try{Thread.sleep(350);}catch(InterruptedException ignored){}
                final String progress="Processed "+results.size()+"/"+ids.size()+" products…";
                main.post(()->resultView.setText(progress+"\n"+join(results)));
            }
            final int success=ok; final String finalText="Finished: "+ok+"/"+ids.size()+" added.\n\n"+join(results)+"\n\nRefresh your FirstCry cart to verify.";
            main.post(()->{addButton.setEnabled(true);addAllButton.setEnabled(true);addButton.setText("ADD TO CART");resultView.setText(finalText);});
        });
    }

    private void confirmAddToAll() {
        if(accounts.isEmpty()){toast("Add at least one account first.");return;}
        String raw=idsInput.getText().toString();
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        Matcher m=Pattern.compile("\\d+").matcher(raw); while(m.find()) ids.add(m.group());
        if(ids.isEmpty()){toast("Enter at least one numeric product ID.");return;}
        new AlertDialog.Builder(this)
            .setTitle("Add to all saved accounts?")
            .setMessage("This will add "+ids.size()+" product(s) to the carts of all "+accounts.size()+" saved account(s). Existing carts will not be cleared.")
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("CONTINUE",(d,w)->startAddToAll(new ArrayList<>(ids),new ArrayList<>(accounts)))
            .show();
    }

    private void startAddToAll(ArrayList<String> ids, ArrayList<Account> allAccounts) {
        addButton.setEnabled(false);
        addAllButton.setEnabled(false);
        addAllButton.setText("ADDING TO ALL ACCOUNTS…");
        resultView.setText("Starting: "+ids.size()+" product(s) × "+allAccounts.size()+" account(s)…");
        executor.execute(()->{
            ArrayList<String> results=new ArrayList<>();
            int success=0, failed=0, completed=0;
            for(Account account:allAccounts){
                try {
                    CurlRequest request=parseCurl(account.curl);
                    int accountOk=0;
                    for(String id:ids){
                        try {
                            String result=addOne(id,request);
                            results.add("✓  "+account.name+" · "+id+" · "+result);
                            accountOk++; success++;
                        } catch(Exception e) {
                            results.add("✕  "+account.name+" · "+id+" · "+e.getMessage());
                            failed++;
                        }
                        completed++;
                        final String progress="Processed "+completed+"/"+(ids.size()*allAccounts.size())+" account/product combinations…";
                        final String snapshot=join(results);
                        main.post(()->resultView.setText(progress+"\n"+snapshot));
                        try{Thread.sleep(350);}catch(InterruptedException ignored){}
                    }
                } catch(Exception e) {
                    for(String id:ids){
                        results.add("✕  "+account.name+" · "+id+" · Account cURL error: "+e.getMessage());
                        failed++; completed++;
                    }
                    final String progress="Processed "+completed+"/"+(ids.size()*allAccounts.size())+" account/product combinations…";
                    final String snapshot=join(results);
                    main.post(()->resultView.setText(progress+"\n"+snapshot));
                }
            }
            final String finalText="Finished: "+success+" added, "+failed+" failed across "+allAccounts.size()+" account(s).\\n\\n"+join(results)+"\\n\\nRefresh each FirstCry cart to verify.";
            main.post(()->{
                addButton.setEnabled(true); addAllButton.setEnabled(true);
                addAllButton.setText("ADD TO ALL ACCOUNT CART");
                resultView.setText(finalText);
            });
        });
    }

    private String addOne(String id,CurlRequest req) throws Exception {
        JSONObject firstBody=new JSONObject(req.body.toString());
        JSONObject obj=firstBody.getJSONObject("objdd");obj.put("ProductID",id);
        JSONObject first=postJson("https://www.firstcry.com/capinet/pdp/SaveProductCart",firstBody,req.headers);
        Object firstResult=first.opt("ProductCartResult");
        if(!isTrue(firstResult)) throw new Exception("SaveProductCart: "+first.toString());
        JSONObject secondBody=new JSONObject();
        secondBody.put("ftk",firstBody.optString("ftk"));
        secondBody.put("viewid",obj.optString("ViewID",""));
        secondBody.put("productid",id);
        secondBody.put("quantity","1");
        secondBody.put("offertype","NO");
        secondBody.put("offerid","");
        secondBody.put("action","add");
        secondBody.put("gcoffer","");
        JSONObject second=postJson("https://www.firstcry.com/svcs/CommonService.svc/SaveCartDetail",secondBody,req.headers);
        if(!isTrue(second.opt("UserCartDetailResult"))) throw new Exception("SaveCartDetail: "+second.toString());
        return "confirmed by both API responses";
    }

    private JSONObject postJson(String url,JSONObject body,Map<String,String> headers) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setRequestMethod("POST");conn.setConnectTimeout(20000);conn.setReadTimeout(25000);conn.setDoOutput(true);
        conn.setRequestProperty("Accept","application/json, text/javascript, */*; q=0.01");
        conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        conn.setRequestProperty("X-Requested-With","XMLHttpRequest");
        conn.setRequestProperty("Referer",headers.containsKey("referer")?headers.get("referer"):"https://www.firstcry.com/");
        conn.setRequestProperty("User-Agent",headers.containsKey("user-agent")?headers.get("user-agent"):"Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/157.0.0.0 Mobile Safari/537.36");
        if(headers.containsKey("cookie"))conn.setRequestProperty("Cookie",headers.get("cookie"));
        if(headers.containsKey("origin"))conn.setRequestProperty("Origin",headers.get("origin"));
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
        try(OutputStream os=conn.getOutputStream()){os.write(bytes);}
        int code=conn.getResponseCode();
        InputStream in=(code>=200&&code<400)?conn.getInputStream():conn.getErrorStream();
        String response="";
        if(in!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);response=out.toString("UTF-8");in.close();}
        conn.disconnect();
        try{return new JSONObject(response);}catch(Exception e){throw new Exception("HTTP "+code+"; non-JSON response: "+response);}
    }

    private boolean isTrue(Object o){return Boolean.TRUE.equals(o)||"true".equalsIgnoreCase(String.valueOf(o))||"1".equals(String.valueOf(o));}

    static class CurlRequest { JSONObject body; Map<String,String> headers=new HashMap<>(); }

    private CurlRequest parseCurl(String curl) throws Exception {
        if(curl==null||curl.trim().isEmpty())throw new Exception("Paste the account's cURL.");
        String s=curl.replaceAll("\\\\\\r?\\n"," ").trim();
        Matcher url=Pattern.compile("(?:--url\\s+)?(['\\\"])(https?://[^'\\\"]+)\\1",Pattern.CASE_INSENSITIVE).matcher(s);
        if(!url.find()||!url.group(2).contains("/capinet/pdp/SaveProductCart"))throw new Exception("This must be the SaveProductCart cURL.");
        Matcher h=Pattern.compile("-H\\s+(['\\\"])(.*?)\\1",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s);
        while(h.find()){
            String line=h.group(2);int colon=line.indexOf(':');
            if(colon>0) {
                String key=line.substring(0,colon).trim().toLowerCase(Locale.ROOT);
                String value=line.substring(colon+1).trim();
                if(key.equals("referer")||key.equals("user-agent")||key.equals("cookie")||key.equals("origin")) reqHeaderPlaceholder();
                // all headers parsed; only safe/needed headers are sent by postJson
                if(key.equals("referer")||key.equals("user-agent")||key.equals("cookie")||key.equals("origin")) parsedHeaders.put(key,value);
            }
        }
        Matcher data=Pattern.compile("--data-raw\\s+(['\\\"])(.*?)\\1",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s);
        boolean foundData=data.find();
        if(!foundData) {
            data=Pattern.compile("--data\\s+(['\\\"])(.*?)\\1",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(s);
            foundData=data.find();
        }
        if(!foundData)throw new Exception("Could not find --data-raw JSON body.");
        CurlRequest r=new CurlRequest();
        r.body=new JSONObject(data.group(2));
        r.headers.putAll(parsedHeaders); parsedHeaders.clear();
        if(!r.body.has("ftk")||!r.body.has("objdd"))throw new Exception("Request body must contain ftk and objdd.");
        return r;
    }
    // Temporary parser accumulator is cleared after each parse.
    private final Map<String,String> parsedHeaders=new HashMap<>();
    private void reqHeaderPlaceholder(){}

    private void styleSpinner(Spinner s){s.setBackground(round(PANEL,8,Color.rgb(55,55,55)));}
    private void styleField(EditText e){e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(round(PANEL,8,Color.rgb(65,65,65)));}
    private GradientDrawable round(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextColor(WHITE);b.setTextSize(12);b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setBackground(round(primary?RED:PANEL,8,primary?RED:Color.rgb(65,65,65)));return b;}
    private void addLabel(LinearLayout p,String s){TextView t=new TextView(this);t.setText(s);t.setTextColor(MUTED);t.setTextSize(11);t.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams lp=matchWrap();lp.bottomMargin=dp(6);p.addView(t,lp);}
    private void addSpace(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void addGap(LinearLayout p,int w){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(dp(w),1));}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private String join(List<String> s){StringBuilder b=new StringBuilder();for(String x:s){if(b.length()>0)b.append("\n");b.append(x);}return b.toString();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){super.onDestroy();executor.shutdown();}
}
