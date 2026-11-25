package com.firstcry.quickcart;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.media.RingtoneManager;
import android.os.Vibrator;
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
    private static final int BG = Color.BLACK;
    private static final int PANEL = Color.rgb(28,28,30);
    private static final int FIELD = Color.rgb(44,44,46);
    private static final int DIV = Color.rgb(44,44,46);
    private static final int RED = Color.rgb(227,38,54);
    private static final int WHITE = Color.rgb(245,245,245);
    private static final int MUTED = Color.rgb(160,160,160);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private final ArrayList<Account> accounts = new ArrayList<>();
    private LinearLayout accountRow;
    private TextView accountValue;
    private TextView accountHint, resultView;
    private EditText idsInput;
    private Button addButton, addAllButton;
    private int selectedIndex = -1;

    static class Account {
        String name;
        String curl;
        String loadCurl = "";
        Account(String n, String c, String l) { name=n; curl=c; loadCurl=(l==null?"":l); }
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject(); o.put("name", name); o.put("curl", curl); o.put("loadCurl", loadCurl); return o;
        }
        static Account fromJson(JSONObject o) {
            return new Account(o.optString("name"), o.optString("curl"), o.optString("loadCurl"));
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
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
            int sys = insets.getSystemWindowInsetBottom(), stable = insets.getStableInsetBottom();
            boolean keyboard = sys - stable > dp(100);
            v.setPadding(0, insets.getStableInsetTop(), 0, sys);
            if (island != null) island.setVisibility(keyboard ? View.GONE : View.VISIBLE);
            return insets;
        });
        setContentView(root);

        homeScroll = pageScroll();
        LinearLayout page = pageColumn(homeScroll);
        page.addView(largeTitle("Quick Cart"));
        addSpace(page, 16);

        addLabel(page, "ACCOUNT");
        accountRow = new LinearLayout(this);
        accountRow.setGravity(Gravity.CENTER_VERTICAL);
        accountRow.setPadding(dp(16), 0, dp(14), 0);
        accountRow.setBackground(ripple(PANEL, 14));
        accountRow.setClickable(true);
        accountRow.addView(text("Account", 16, WHITE));
        accountValue = text("", 16, MUTED);
        accountValue.setGravity(Gravity.END);
        accountValue.setSingleLine(true);
        accountValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, -2, 1);
        vp.leftMargin = dp(12);
        accountRow.addView(accountValue, vp);
        TextView chev = text("›", 24, MUTED);
        chev.setPadding(dp(8), 0, 0, dp(2));
        accountRow.addView(chev);
        page.addView(accountRow, new LinearLayout.LayoutParams(-1, dp(52)));
        accountRow.setOnClickListener(v -> showAccountPicker());
        accountHint = footnote("");
        page.addView(accountHint);

        addSpace(page, 12);
        LinearLayout accountActions = new LinearLayout(this);
        Button addAccount = button("Add Account", true);
        Button editAccount = button("Edit", false);
        Button deleteAccount = button("Delete", false);
        deleteAccount.setTextColor(RED);
        accountActions.addView(addAccount, new LinearLayout.LayoutParams(0, dp(50), 1.3f));
        addGap(accountActions, 8);
        accountActions.addView(editAccount, new LinearLayout.LayoutParams(0, dp(50), 0.8f));
        addGap(accountActions, 8);
        accountActions.addView(deleteAccount, new LinearLayout.LayoutParams(0, dp(50), 0.8f));
        page.addView(accountActions);
        addAccount.setOnClickListener(v -> showAccountDialog(-1));
        editAccount.setOnClickListener(v -> { if(selectedIndex>=0) showAccountDialog(selectedIndex); else toast("Add an account first."); });
        deleteAccount.setOnClickListener(v -> confirmDelete());

        addSpace(page, 28);
        addLabel(page, "PRODUCT IDS");
        idsInput = new EditText(this);
        idsInput.setHint("24961441\n24961454\n24476798");
        idsInput.setTextSize(16);
        idsInput.setGravity(Gravity.TOP | Gravity.LEFT);
        idsInput.setMinLines(5); idsInput.setMaxLines(9);
        idsInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        styleField(idsInput);
        page.addView(idsInput, matchWrap());
        page.addView(footnote("One per line, comma-separated or space-separated"));

        addButton = button("Add to Cart", true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(52)); bp.topMargin = dp(14);
        page.addView(addButton, bp);
        addButton.setOnClickListener(v -> startAdd());

        addAllButton = button("Add to All Accounts", false);
        LinearLayout.LayoutParams allBp = new LinearLayout.LayoutParams(-1, dp(52)); allBp.topMargin = dp(10);
        page.addView(addAllButton, allBp);
        addAllButton.setOnClickListener(v -> confirmAddToAll());

        addSpace(page, 28);
        addLabel(page, "RESULTS");
        resultView = text("Ready. Select an account and enter product IDs.", 13, MUTED);
        resultView.setPadding(dp(16), dp(14), dp(16), dp(14));
        resultView.setBackground(round(PANEL, 14, 0));
        page.addView(resultView, matchWrap());

        addSpace(page, 14);
        page.addView(footnote("Your saved cURL contains an ftk session token. It is stored in this app's private local storage. Do not share it. Tokens may expire; replace the cURL when needed."));

        refreshSpinner();
        buildBookmarksPage();
        buildWatchPage();
        buildIsland();
        showPage(0);
    }

    // ================= Account picker =================
    private void refreshSpinner() {
        if (accountValue == null) return;
        accountValue.setText(selectedIndex >= 0 && selectedIndex < accounts.size() ? accounts.get(selectedIndex).name : "None");
        updateAccountHint();
        updateWatchHint();
    }

    private void updateAccountHint() {
        if (accountHint == null) return;
        if (selectedIndex >= 0 && selectedIndex < accounts.size()) accountHint.setText("Saved locally · " + accounts.get(selectedIndex).name);
        else accountHint.setText("Add an account and paste its SaveProductCart cURL.");
    }

    private void showAccountPicker() {
        if (accounts.isEmpty()) { toast("Add an account first."); return; }
        String[] names = new String[accounts.size()];
        for (int i = 0; i < names.length; i++) names[i] = accounts.get(i).name;
        dialogBuilder().setTitle("Select Account")
            .setSingleChoiceItems(names, selectedIndex, (d, which) -> {
                selectedIndex = which;
                prefs.edit().putInt("selected", selectedIndex).apply();
                refreshSpinner();
                d.dismiss();
            })
            .setNegativeButton("CANCEL", null).show();
    }

    private AlertDialog.Builder dialogBuilder() { return new AlertDialog.Builder(this, R.style.DialogTheme); }

    // ================= Bookmarks page =================
    private final ArrayList<String> bookmarks = new ArrayList<>();
    private final LinkedHashSet<String> bookmarkChecked = new LinkedHashSet<>();
    private FrameLayout root;
    private ScrollView homeScroll, bookmarksScroll;
    private LinearLayout bookmarkList;
    private TextView bookmarkCount;
    private EditText bookmarkInput;
    private int currentPage = 0;

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

    private void buildBookmarksPage() {
        bookmarksScroll = pageScroll();
        LinearLayout page = pageColumn(bookmarksScroll);
        bookmarksScroll.setVisibility(View.GONE);

        page.addView(largeTitle("Bookmarks"));
        addSpace(page, 16);

        addLabel(page, "ADD PRODUCT IDS");
        bookmarkInput = new EditText(this);
        bookmarkInput.setHint("24961441\n24961454");
        bookmarkInput.setTextSize(16);
        bookmarkInput.setGravity(Gravity.TOP | Gravity.LEFT);
        bookmarkInput.setMinLines(3); bookmarkInput.setMaxLines(6);
        bookmarkInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        styleField(bookmarkInput);
        page.addView(bookmarkInput, matchWrap());
        page.addView(footnote("One per line, comma-separated or space-separated"));

        Button save = button("Save to Bookmarks", true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52)); sp.topMargin = dp(14);
        page.addView(save, sp);
        save.setOnClickListener(v -> saveBookmarkInput());

        addSpace(page, 28);
        bookmarkCount = makeLabel("");
        LinearLayout.LayoutParams cp = matchWrap(); cp.bottomMargin = dp(8);
        page.addView(bookmarkCount, cp);

        bookmarkList = new LinearLayout(this);
        bookmarkList.setOrientation(LinearLayout.VERTICAL);
        bookmarkList.setBackground(round(PANEL, 14, 0));
        page.addView(bookmarkList, matchWrap());

        LinearLayout selRow = new LinearLayout(this);
        Button selAll = button("Select All", false);
        Button selNone = button("Clear", false);
        selRow.addView(selAll, new LinearLayout.LayoutParams(0, dp(46), 1));
        addGap(selRow, 8);
        selRow.addView(selNone, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams rp = matchWrap(); rp.topMargin = dp(12);
        page.addView(selRow, rp);
        selAll.setOnClickListener(v -> { bookmarkChecked.addAll(bookmarks); refreshBookmarkList(); });
        selNone.setOnClickListener(v -> { bookmarkChecked.clear(); refreshBookmarkList(); });

        Button use = button("Add Selected to Cart", true);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, dp(52)); up.topMargin = dp(10);
        page.addView(use, up);
        use.setOnClickListener(v -> useSelectedBookmarks(idsInput, 0));
        Button useWatch = button("Watch Selected", false);
        LinearLayout.LayoutParams uw = new LinearLayout.LayoutParams(-1, dp(50)); uw.topMargin = dp(10);
        page.addView(useWatch, uw);
        useWatch.setOnClickListener(v -> useSelectedBookmarks(watchInput, 2));

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
        bookmarkCount.setText("SAVED (" + bookmarks.size() + ")");
        if (bookmarks.isEmpty()) {
            TextView e = text("No bookmarks yet. Add product IDs above.", 14, MUTED);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(16), dp(24), dp(16), dp(24));
            bookmarkList.addView(e, matchWrap());
            return;
        }
        boolean first = true;
        for (final String id : bookmarks) {
            if (!first) {
                View line = new View(this);
                line.setBackgroundColor(DIV);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
                lp.leftMargin = dp(16);
                bookmarkList.addView(line, lp);
            }
            first = false;

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), 0, dp(2), 0);

            CheckBox cb = new CheckBox(this);
            cb.setText(id); cb.setTextColor(WHITE); cb.setTextSize(16);
            cb.setButtonTintList(ColorStateList.valueOf(RED));
            cb.setChecked(bookmarkChecked.contains(id));
            cb.setOnCheckedChangeListener((b, c) -> { if (c) bookmarkChecked.add(id); else bookmarkChecked.remove(id); });
            row.addView(cb, new LinearLayout.LayoutParams(0, dp(52), 1));

            TextView rm = text("✕", 17, MUTED);
            rm.setGravity(Gravity.CENTER);
            rm.setClickable(true);
            rm.setOnClickListener(v -> { bookmarks.remove(id); bookmarkChecked.remove(id); saveBookmarks(); });
            row.addView(rm, new LinearLayout.LayoutParams(dp(52), dp(52)));
            bookmarkList.addView(row, matchWrap());
        }
    }

    private void useSelectedBookmarks(EditText target, int page) {
        if (bookmarkChecked.isEmpty()) { toast("Tick at least one bookmark."); return; }
        StringBuilder sb = new StringBuilder(target.getText().toString().trim());
        int n = 0;
        for (String id : bookmarks) {
            if (bookmarkChecked.contains(id)) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(id); n++;
            }
        }
        target.setText(sb.toString());
        target.setSelection(target.length());
        showPage(page);
        toast(n + " product ID(s) added.");
    }

    // ================= Bottom island (tab bar) =================
    static class Tab { LinearLayout view; TabIcon icon; TextView label; }

    static class TabIcon extends View {
        static final int CART = 0, BOOKMARK = 1, WATCH = 2;
        final int type;
        boolean selected = false;
        int color = Color.GRAY;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        TabIcon(Context c, int type) {
            super(c);
            this.type = type;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        void set(boolean sel, int col) { selected = sel; color = col; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(color);
            p.setStrokeWidth(w * 0.085f);
            Paint.Style body = selected ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE;
            path.reset();
            if (type == BOOKMARK) {
                path.moveTo(w * .26f, h * .12f);
                path.lineTo(w * .74f, h * .12f);
                path.lineTo(w * .74f, h * .88f);
                path.lineTo(w * .50f, h * .68f);
                path.lineTo(w * .26f, h * .88f);
                path.close();
                p.setStyle(body);
                c.drawPath(path, p);
            } else if (type == WATCH) {
                path.moveTo(w * .06f, h * .50f);
                path.quadTo(w * .50f, h * -.02f, w * .94f, h * .50f);
                path.quadTo(w * .50f, h * 1.02f, w * .06f, h * .50f);
                path.close();
                p.setStyle(body);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(selected ? Color.rgb(44, 44, 46) : color);
                c.drawCircle(w * .50f, h * .50f, w * .14f, p);
            } else {
                path.moveTo(w * .22f, h * .28f);
                path.lineTo(w * .92f, h * .28f);
                path.lineTo(w * .82f, h * .64f);
                path.lineTo(w * .32f, h * .64f);
                path.close();
                p.setStyle(body);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(w * .06f, h * .14f);
                path.lineTo(w * .18f, h * .14f);
                path.lineTo(w * .22f, h * .28f);
                p.setStyle(Paint.Style.STROKE);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(w * .40f, h * .84f, w * .07f, p);
                c.drawCircle(w * .76f, h * .84f, w * .07f, p);
            }
        }
    }

    private LinearLayout island;
    private Tab tabCart, tabMarks;

    private void buildIsland() {
        island = new LinearLayout(this);
        island.setGravity(Gravity.CENTER);
        island.setPadding(dp(8), dp(8), dp(8), dp(8));
        island.setBackground(round(PANEL, 36, Color.rgb(56, 56, 58)));
        island.setElevation(dp(12));

        tabCart = makeTab("Cart", TabIcon.CART);
        tabMarks = makeTab("Bookmarks", TabIcon.BOOKMARK);
        tabWatch = makeTab("Watch", TabIcon.WATCH);
        island.addView(tabCart.view, new LinearLayout.LayoutParams(dp(88), dp(58)));
        addGap(island, 4);
        island.addView(tabMarks.view, new LinearLayout.LayoutParams(dp(88), dp(58)));
        addGap(island, 4);
        island.addView(tabWatch.view, new LinearLayout.LayoutParams(dp(88), dp(58)));
        tabCart.view.setOnClickListener(v -> showPage(0));
        tabMarks.view.setOnClickListener(v -> showPage(1));
        tabWatch.view.setOnClickListener(v -> showPage(2));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(14);
        root.addView(island, lp);
    }

    private Tab makeTab(String label, int type) {
        Tab t = new Tab();
        t.view = new LinearLayout(this);
        t.view.setOrientation(LinearLayout.VERTICAL);
        t.view.setGravity(Gravity.CENTER);
        t.view.setClickable(true);
        t.icon = new TabIcon(this, type);
        t.view.addView(t.icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        t.label = text(label, 11, MUTED);
        t.label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(3);
        t.view.addView(t.label, lp);
        return t;
    }

    private void styleTab(Tab t, boolean sel) {
        t.view.setBackground(sel ? round(Color.rgb(44, 44, 46), 29, 0) : null);
        int c = sel ? RED : MUTED;
        t.icon.set(sel, c);
        t.label.setTextColor(c);
    }

    private void showPage(int page) {
        currentPage = page;
        ScrollView[] pages = {homeScroll, bookmarksScroll, watchScroll};
        for (int i = 0; i < pages.length; i++) {
            if (i == page) {
                pages[i].setAlpha(0f);
                pages[i].setVisibility(View.VISIBLE);
                pages[i].animate().alpha(1f).setDuration(160);
            } else pages[i].setVisibility(View.GONE);
        }
        styleTab(tabCart, page == 0);
        styleTab(tabMarks, page == 1);
        styleTab(tabWatch, page == 2);
        if (page == 2) updateWatchHint();
        hideKeyboard();
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    @Override public void onBackPressed() {
        if (currentPage != 0) showPage(0);
        else super.onBackPressed();
    }

    // ================= Watch (stock polling) =================
    private ScrollView watchScroll;
    private EditText watchInput;
    private Button watchButton;
    private TextView watchStatus, watchHint;
    private Tab tabWatch;
    private int watchInterval = 30;
    private static final int[] SEG = {10, 30, 60, 120};
    private final ArrayList<TextView> segViews = new ArrayList<>();
    private volatile boolean watching = false;
    private final ExecutorService watchExecutor = Executors.newSingleThreadExecutor();

    static class LoadRequest { String url; JSONObject body; Map<String,String> headers = new HashMap<>(); }

    private LoadRequest parseLoadCurl(String curl) throws Exception {
        if (curl == null || curl.trim().isEmpty()) throw new Exception("Paste the LoadCart cURL.");
        String s = curl.replaceAll("\\\\\\r?\\n", " ").trim();
        Matcher url = Pattern.compile("(?:--url\\s+)?(['\"])(https?://[^'\"]+)\\1", Pattern.CASE_INSENSITIVE).matcher(s);
        if (!url.find() || !url.group(2).contains("LoadCart")) throw new Exception("This must be the LoadCart cURL.");
        LoadRequest r = new LoadRequest();
        r.url = url.group(2);
        Matcher h = Pattern.compile("-H\\s+(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
        while (h.find()) {
            String line = h.group(2);
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (key.equals("referer") || key.equals("user-agent") || key.equals("cookie") || key.equals("origin")
                    || key.equals("fingerprint") || key.equals("accept-language")) r.headers.put(key, value);
            }
        }
        Matcher data = Pattern.compile("--data-raw\\s+(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
        boolean found = data.find();
        if (!found) {
            data = Pattern.compile("--data\\s+(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(s);
            found = data.find();
        }
        if (!found) throw new Exception("Could not find --data-raw JSON body.");
        r.body = new JSONObject(data.group(2));
        if (!r.body.has("pro")) throw new Exception("Request body must contain the pro field.");
        return r;
    }

    // Calls LoadCart for the given product IDs and returns the ones present in PurchaseOrderItemList.
    private Set<String> checkCart(LoadRequest lr, Collection<String> ids) throws Exception {
        JSONObject body = new JSONObject(lr.body.toString());
        StringBuilder pro = new StringBuilder();
        for (String id : ids) { if (pro.length() > 0) pro.append("*"); pro.append("NO^").append(id).append("^1^0"); }
        body.put("pro", pro.toString());
        JSONObject res = postJson(lr.url, body, lr.headers);
        JSONObject r = res.optJSONObject("LoadCartResult");
        JSONObject os = r == null ? null : r.optJSONObject("pOrderSummary");
        JSONArray list = os == null ? null : os.optJSONArray("PurchaseOrderItemList");
        if (list == null) throw new Exception("No PurchaseOrderItemList: " + shorten(res.toString()));
        HashSet<String> found = new HashSet<>();
        for (int i = 0; i < list.length(); i++) collectIds(list.opt(i), ids, found);
        return found;
    }

    private void collectIds(Object o, Collection<String> ids, Set<String> found) {
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            Iterator<String> it = j.keys();
            while (it.hasNext()) collectIds(j.opt(it.next()), ids, found);
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) collectIds(a.opt(i), ids, found);
        } else if (o != null && o != JSONObject.NULL) {
            String v = String.valueOf(o).trim();
            if (ids.contains(v)) found.add(v);
        }
    }

    private void buildWatchPage() {
        watchScroll = pageScroll();
        LinearLayout page = pageColumn(watchScroll);
        watchScroll.setVisibility(View.GONE);

        page.addView(largeTitle("Watch"));
        addSpace(page, 16);

        addLabel(page, "ACCOUNT");
        watchHint = text("", 15, WHITE);
        watchHint.setPadding(dp(16), dp(14), dp(16), dp(14));
        watchHint.setBackground(round(PANEL, 14, 0));
        page.addView(watchHint, matchWrap());
        page.addView(footnote("Uses the account selected on the Cart page. It needs a LoadCart cURL saved in that account (Cart → Edit)."));

        addSpace(page, 28);
        addLabel(page, "PRODUCT IDS");
        watchInput = new EditText(this);
        watchInput.setHint("24961441\n24961454");
        watchInput.setTextSize(16);
        watchInput.setGravity(Gravity.TOP | Gravity.LEFT);
        watchInput.setMinLines(3); watchInput.setMaxLines(8);
        watchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        styleField(watchInput);
        page.addView(watchInput, matchWrap());
        page.addView(footnote("Each check adds the product to the cart, then confirms it in LoadCart. Confirmed IDs stop being watched."));

        addSpace(page, 28);
        addLabel(page, "CHECK EVERY");
        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(round(PANEL, 12, 0));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < SEG.length; i++) {
            final int secs = SEG[i];
            TextView t = text(secs < 60 ? secs + " sec" : (secs / 60) + " min", 14, WHITE);
            t.setGravity(Gravity.CENTER);
            t.setClickable(true);
            t.setOnClickListener(v -> { watchInterval = secs; styleSegments(); });
            segViews.add(t);
            seg.addView(t, new LinearLayout.LayoutParams(0, dp(38), 1));
        }
        page.addView(seg, matchWrap());
        styleSegments();

        watchButton = button("Start Watching", true);
        LinearLayout.LayoutParams wb = new LinearLayout.LayoutParams(-1, dp(52)); wb.topMargin = dp(18);
        page.addView(watchButton, wb);
        watchButton.setOnClickListener(v -> toggleWatch());

        addSpace(page, 28);
        addLabel(page, "STATUS");
        watchStatus = text("Not watching.", 13, MUTED);
        watchStatus.setPadding(dp(16), dp(14), dp(16), dp(14));
        watchStatus.setBackground(round(PANEL, 14, 0));
        page.addView(watchStatus, matchWrap());
        page.addView(footnote("Keep the app open while watching; the screen stays on. Very frequent checks can get an account rate-limited, so 30 seconds or slower is safer."));
        updateWatchHint();
    }

    private void styleSegments() {
        for (int i = 0; i < segViews.size(); i++) {
            boolean sel = SEG[i] == watchInterval;
            TextView t = segViews.get(i);
            t.setBackground(sel ? round(FIELD, 9, 0) : null);
            t.setTextColor(sel ? WHITE : MUTED);
        }
    }

    private void updateWatchHint() {
        if (watchHint == null) return;
        if (selectedIndex < 0 || selectedIndex >= accounts.size()) {
            watchHint.setText("No account selected"); watchHint.setTextColor(RED); return;
        }
        Account a = accounts.get(selectedIndex);
        boolean ok = a.loadCurl != null && !a.loadCurl.trim().isEmpty();
        watchHint.setText(a.name + "  ·  " + (ok ? "LoadCart saved ✓" : "LoadCart cURL missing ✕"));
        watchHint.setTextColor(ok ? WHITE : RED);
    }

    private void toggleWatch() {
        if (watching) { watching = false; return; }
        if (selectedIndex < 0 || selectedIndex >= accounts.size()) { toast("Select or add an account first."); return; }
        final Account account = accounts.get(selectedIndex);
        if (account.loadCurl == null || account.loadCurl.trim().isEmpty()) { toast("Edit the account and paste its LoadCart cURL first."); return; }
        final LinkedHashSet<String> ids = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\d+").matcher(watchInput.getText().toString());
        while (m.find()) ids.add(m.group());
        if (ids.isEmpty()) { toast("Enter at least one numeric product ID."); return; }
        final CurlRequest addReq;
        final LoadRequest loadReq;
        try { addReq = parseCurl(account.curl); loadReq = parseLoadCurl(account.loadCurl); }
        catch (Exception e) { toast("Saved cURL needs updating: " + e.getMessage()); return; }
        watching = true;
        watchButton.setText("Stop Watching");
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final int interval = watchInterval;
        hideKeyboard();
        watchExecutor.execute(() -> runWatch(account.name, ids, addReq, loadReq, interval));
    }

    private void runWatch(String accountName, LinkedHashSet<String> ids, CurlRequest addReq, LoadRequest loadReq, int interval) {
        LinkedHashMap<String, String> state = new LinkedHashMap<>();
        LinkedHashSet<String> pending = new LinkedHashSet<>(ids);   // not added yet (add call failing = not available)
        LinkedHashSet<String> added = new LinkedHashSet<>();        // added, waiting to appear in LoadCart
        for (String id : ids) state.put(id, "◌  not checked yet");
        int round = 0;
        while (watching && (!pending.isEmpty() || !added.isEmpty())) {
            round++;
            final String checking = renderWatch(accountName, round, clock(), "Checking…", state);
            main.post(() -> watchStatus.setText(checking));
            boolean newHit = false;
            for (String id : new ArrayList<>(pending)) {
                if (!watching) break;
                try {
                    addOne(id, addReq);
                    pending.remove(id);
                    added.add(id);
                    state.put(id, "＋  added to cart · confirming…");
                } catch (Exception e) {
                    state.put(id, "⏳  not available yet · " + shorten(e.getMessage()));
                }
                sleepMs(350);
            }
            if (watching && !added.isEmpty()) {
                try {
                    Set<String> found = checkCart(loadReq, added);
                    for (String id : found) { added.remove(id); state.put(id, "✓  IN STOCK · confirmed in cart"); newHit = true; }
                    for (String id : added) state.put(id, "＋  added · not in LoadCart yet");
                } catch (Exception e) {
                    for (String id : added) state.put(id, "＋  added · LoadCart error: " + shorten(e.getMessage()));
                }
            }
            if (newHit) main.post(this::alertUser);
            if (pending.isEmpty() && added.isEmpty()) break;
            final int r = round;
            final String stamp = clock();
            for (int s = interval; s > 0 && watching; s--) {
                final String t = renderWatch(accountName, r, stamp, "Next check in " + s + "s", state);
                main.post(() -> watchStatus.setText(t));
                sleepMs(1000);
            }
        }
        final boolean done = pending.isEmpty() && added.isEmpty();
        final String finalText = renderWatch(accountName, round, clock(), done ? "Finished: all items confirmed." : "Stopped.", state);
        main.post(() -> {
            watching = false;
            watchButton.setText("Start Watching");
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            watchStatus.setText(finalText);
        });
    }

    private String renderWatch(String acc, int round, String stamp, String footer, Map<String, String> state) {
        StringBuilder b = new StringBuilder();
        b.append(acc).append(" · round ").append(round).append(" · ").append(stamp).append("\n").append(footer).append("\n");
        for (Map.Entry<String, String> e : state.entrySet()) b.append("\n").append(e.getKey()).append("   ").append(e.getValue());
        return b.toString();
    }

    private void alertUser() {
        toast("Item in stock and added to cart!");
        try { RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play(); } catch (Exception ignored) {}
        try { Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); if (vb != null) vb.vibrate(600); } catch (Exception ignored) {}
    }

    private String clock() { return new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()); }
    private String shorten(String s) { if (s == null) return "error"; s = s.replace("\n", " "); return s.length() > 70 ? s.substring(0, 70) + "…" : s; }
    private void sleepMs(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    // ================= Small UI helpers =================
    private ScrollView pageScroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(BG);
        s.setVerticalScrollBarEnabled(false);
        root.addView(s, new FrameLayout.LayoutParams(-1, -1));
        return s;
    }

    private LinearLayout pageColumn(ScrollView s) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(14), dp(18), dp(130));
        page.setBackgroundColor(BG);
        s.addView(page);
        return page;
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        return t;
    }

    private TextView largeTitle(String s) {
        TextView t = text(s, 34, WHITE);
        t.setTypeface(null, Typeface.BOLD);
        t.setLetterSpacing(-0.02f);
        t.setPadding(dp(4), dp(8), 0, 0);
        return t;
    }

    private TextView footnote(String s) {
        TextView t = text(s, 12, MUTED);
        t.setPadding(dp(16), dp(7), dp(16), 0);
        return t;
    }

    private TextView makeLabel(String s) {
        TextView t = text(s, 12, MUTED);
        t.setPadding(dp(16), 0, dp(16), 0);
        return t;
    }

    private Drawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), round(color, radius, 0), round(Color.WHITE, radius, 0));
    }

    private void showAccountDialog(int index) {
        boolean editing=index>=0;
        Account old=editing?accounts.get(index):null;
        LinearLayout box=new LinearLayout(this); box.setPadding(dp(18),dp(8),dp(18),dp(2)); box.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("Account name (e.g. FC1 – Paru)");
        EditText curl=new EditText(this); curl.setHint("Paste complete SaveProductCart cURL here"); curl.setMinLines(6); curl.setMaxLines(10); curl.setGravity(Gravity.TOP);
        EditText lcurl=new EditText(this); lcurl.setHint("Optional: LoadCart cURL (needed for Watch)"); lcurl.setMinLines(4); lcurl.setMaxLines(8); lcurl.setGravity(Gravity.TOP);
        EditText[] fields={name,curl,lcurl};
        for(EditText f:fields){f.setTextColor(WHITE);f.setHintTextColor(MUTED);styleField(f);f.setBackground(round(FIELD,10,0));}
        if(editing){name.setText(old.name);curl.setText(old.curl);lcurl.setText(old.loadCurl);}
        box.addView(name,matchWrap()); addSpace(box,8); box.addView(curl,matchWrap()); addSpace(box,8); box.addView(lcurl,matchWrap());
        ScrollView sv=new ScrollView(this); sv.addView(box);
        AlertDialog dialog=dialogBuilder().setTitle(editing?"Edit account":"Add FirstCry account")
            .setView(sv).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=name.getText().toString().trim(), c=curl.getText().toString().trim(), lc=lcurl.getText().toString().trim();
            if(n.isEmpty()){name.setError("Enter an account name");return;}
            try{parseCurl(c);}catch(Exception e){curl.setError(e.getMessage());return;}
            if(!lc.isEmpty()){try{parseLoadCurl(lc);}catch(Exception e){lcurl.setError(e.getMessage());return;}}
            if(editing){accounts.set(index,new Account(n,c,lc));selectedIndex=index;}
            else{accounts.add(new Account(n,c,lc));selectedIndex=accounts.size()-1;}
            saveAccounts();dialog.dismiss();toast("Account saved.");
        }));
        dialog.show();
    }

    private void confirmDelete() {
        if(selectedIndex<0||selectedIndex>=accounts.size()){toast("No account selected.");return;}
        String n=accounts.get(selectedIndex).name;
        dialogBuilder().setTitle("Delete account?")
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
        addButton.setEnabled(false);addAllButton.setEnabled(false);addButton.setText("Adding…");
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
            main.post(()->{addButton.setEnabled(true);addAllButton.setEnabled(true);addButton.setText("Add to Cart");resultView.setText(finalText);});
        });
    }

    private void confirmAddToAll() {
        if(accounts.isEmpty()){toast("Add at least one account first.");return;}
        String raw=idsInput.getText().toString();
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        Matcher m=Pattern.compile("\\d+").matcher(raw); while(m.find()) ids.add(m.group());
        if(ids.isEmpty()){toast("Enter at least one numeric product ID.");return;}
        dialogBuilder()
            .setTitle("Add to all saved accounts?")
            .setMessage("This will add "+ids.size()+" product(s) to the carts of all "+accounts.size()+" saved account(s). Existing carts will not be cleared.")
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("CONTINUE",(d,w)->startAddToAll(new ArrayList<>(ids),new ArrayList<>(accounts)))
            .show();
    }

    private void startAddToAll(ArrayList<String> ids, ArrayList<Account> allAccounts) {
        addButton.setEnabled(false);
        addAllButton.setEnabled(false);
        addAllButton.setText("Adding to All Accounts…");
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
            final String finalText="Finished: "+success+" added, "+failed+" failed across "+allAccounts.size()+" account(s).\n\n"+join(results)+"\n\nRefresh each FirstCry cart to verify.";
            main.post(()->{
                addButton.setEnabled(true); addAllButton.setEnabled(true);
                addAllButton.setText("Add to All Accounts");
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
        if(!url.contains("fcappservices.in"))conn.setRequestProperty("X-Requested-With","XMLHttpRequest");
        conn.setRequestProperty("Referer",headers.containsKey("referer")?headers.get("referer"):"https://www.firstcry.com/");
        conn.setRequestProperty("User-Agent",headers.containsKey("user-agent")?headers.get("user-agent"):"Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/157.0.0.0 Mobile Safari/537.36");
        if(headers.containsKey("cookie"))conn.setRequestProperty("Cookie",headers.get("cookie"));
        if(headers.containsKey("origin"))conn.setRequestProperty("Origin",headers.get("origin"));
        if(headers.containsKey("fingerprint"))conn.setRequestProperty("fingerprint",headers.get("fingerprint"));
        if(headers.containsKey("accept-language"))conn.setRequestProperty("Accept-Language",headers.get("accept-language"));
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

    private void styleField(EditText e){e.setTextColor(WHITE);e.setHintTextColor(Color.GRAY);e.setPadding(dp(16),dp(13),dp(16),dp(13));e.setBackground(round(PANEL,12,0));}
    private GradientDrawable round(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextColor(WHITE);b.setTextSize(15);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setStateListAnimator(null);b.setMinHeight(0);b.setMinimumHeight(0);b.setBackground(ripple(primary?RED:PANEL,14));return b;}
    private void addLabel(LinearLayout p,String s){TextView t=makeLabel(s);LinearLayout.LayoutParams lp=matchWrap();lp.bottomMargin=dp(8);p.addView(t,lp);}
    private void addSpace(LinearLayout p,int h){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void addGap(LinearLayout p,int w){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(dp(w),1));}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private String join(List<String> s){StringBuilder b=new StringBuilder();for(String x:s){if(b.length()>0)b.append("\n");b.append(x);}return b.toString();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){super.onDestroy();watching=false;executor.shutdown();watchExecutor.shutdownNow();}
}
