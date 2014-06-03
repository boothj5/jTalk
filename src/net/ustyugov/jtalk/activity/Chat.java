/*
 * Copyright (C) 2012, Igor Ustyugov <igor@ustyugov.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/
 */

package net.ustyugov.jtalk.activity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.ActionBar;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import net.ustyugov.jtalk.*;
import net.ustyugov.jtalk.SendFileTask;
import net.ustyugov.jtalk.activity.muc.SubjectActivity;
import net.ustyugov.jtalk.activity.note.TemplatesActivity;
import net.ustyugov.jtalk.activity.vcard.VCardActivity;
import net.ustyugov.jtalk.adapter.*;
import net.ustyugov.jtalk.adapter.muc.MucChatAdapter;
import net.ustyugov.jtalk.adapter.muc.MucUserAdapter;
import net.ustyugov.jtalk.db.JTalkProvider;
import net.ustyugov.jtalk.db.MessageDbHelper;
import net.ustyugov.jtalk.dialog.*;
import net.ustyugov.jtalk.imgur.ImgurUploadTask;
import net.ustyugov.jtalk.listener.DragAndDropListener;
import net.ustyugov.jtalk.service.JTalkService;
import net.ustyugov.jtalk.smiles.Smiles;
import net.ustyugov.jtalk.view.MyListView;

import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ChatState;
import org.jivesoftware.smackx.muc.MultiUserChat;

import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

import com.jtalk2.R;

public class Chat extends Activity implements View.OnClickListener, OnScrollListener {
    public static final int REQUEST_TEMPLATES = 1;
    public static final int REQUEST_FILE = 2;
    public static final int REQUEST_PHOTO = 3;

    private boolean isMuc = false;
    private boolean isPrivate = false;
    private MultiUserChat muc;

    private SharedPreferences prefs;
    private Menu menu;

    private LinearLayout sidebar;
    private LinearLayout attachPanel;
    private ChatAdapter  listAdapter;
    private MucChatAdapter listMucAdapter;
    private OpenChatsAdapter chatsAdapter;
    private ChatsSpinnerAdapter chatsSpinnerAdapter;
    private MucUserAdapter usersAdapter;
    private MyListView listView;
    private ListView chatsList;
    private ListView nickList;
    private EditText messageInput;
    private TextView attachPath;
    private ImageButton sendButton;
    private ImageView removeAttach;

    private String jid;
    private String account;
    private String resource;
    private String searchString = "";
    private boolean compose = false;
    private boolean imgur = false;
    boolean move = false;
    private int maxCount = 0;
    private int unreadMessages = 0;
    private int separatorPosition = 0;

    private BroadcastReceiver textReceiver;
    private BroadcastReceiver finishReceiver;
    private BroadcastReceiver msgReceiver;
    private BroadcastReceiver receivedReceiver;
    private BroadcastReceiver presenceReceiver;
    private BroadcastReceiver composeReceiver;

    private JTalkService service;
    private Smiles smiles;

    private RosterItem rosterItem;
    private ChatAdapter.ViewMode viewMode = ChatAdapter.ViewMode.single;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));
        service = JTalkService.getInstance();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            maxCount = Integer.parseInt(prefs.getString("MaxLogMessages", "0"));
        } catch (NumberFormatException ignored) {	}

        setTheme(Colors.isLight ? R.style.AppThemeLight : R.style.AppThemeDark);

        chatsSpinnerAdapter = new ChatsSpinnerAdapter(this);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setListNavigationCallbacks(chatsSpinnerAdapter, new ActionBar.OnNavigationListener() {
            @Override
            public boolean onNavigationItemSelected(int position, long itemId) {
                RosterItem item = chatsSpinnerAdapter.getItem(position);
                String a = item.getAccount();
                String j = jid;
                if (rosterItem != null && item != rosterItem) {
                    rosterItem = item;
                    if (item.isEntry() || item.isSelf()) j = item.getEntry().getUser();
                    else if (item.isMuc()) j = item.getName();
                    Intent intent = new Intent();
                    intent.putExtra("jid", j);
                    intent.putExtra("account", a);
                    setIntent(intent);
                    onPause();
                    onResume();
                }
                return true;
            }
        });

        setContentView(R.layout.chat);

        LinearLayout linear = (LinearLayout) findViewById(R.id.chat_linear);
        linear.setBackgroundColor(Colors.BACKGROUND);

        smiles = service.getSmiles(this);

        chatsAdapter = new OpenChatsAdapter(this);
        chatsList = (ListView) findViewById(R.id.open_chat_list);
        chatsList.setCacheColorHint(0x00000000);
        chatsList.setDividerHeight(0);
        chatsList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long arg3) {
                if (move) return;
                if (position > 0) {
                    RosterItem item = (RosterItem) parent.getItemAtPosition(position);
                    String j = null;
                    if (item.isEntry()) j = item.getEntry().getUser();
                    else if (item.isMuc()) j = item.getName();
                    if (j != null && !j.equals(jid)) {
                        Intent intent = new Intent();
                        intent.putExtra("account", item.getAccount());
                        intent.putExtra("jid", j);
                        setIntent(intent);
                        onPause();
                        onResume();
                    }
                } else {
                    service.setSidebarMode("users");
                    updateChats();
                }
            }
        });
        chatsList.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                if (move) return false;
                if (position > 0) {
                    RosterItem item = (RosterItem) parent.getItemAtPosition(position);
                    if (item.isEntry()) {
                        RosterEntry entry = item.getEntry();
                        if (entry != null) {
                            String j = entry.getUser();
                            if (service.getConferencesHash(item.getAccount()).containsKey(j)) {
                                String group = StringUtils.parseBareAddress(j);
                                String nick = StringUtils.parseResource(j);
                                MucDialogs.userMenu(Chat.this, item.getAccount(), group, nick);
                            } else RosterDialogs.ContactMenuDialog(Chat.this, item);
                        }
                    } else if (item.isMuc()) {
                        MucDialogs.roomMenu(Chat.this, item.getAccount(), item.getName());
                    }
                }
                return true;
            }
        });

        listAdapter = new ChatAdapter(this, smiles);
        listMucAdapter = new MucChatAdapter(this, smiles);
        listView = (MyListView) findViewById(R.id.chat_list);
        listView.setFocusable(true);
        listView.setCacheColorHint(0x00000000);
        listView.setOnScrollListener(this);
        listView.setDividerHeight(0);
        listView.setAdapter(listAdapter);
        listView.setOnItemLongClickListener(new DragAndDropListener(this));

        nickList = (ListView) findViewById(R.id.muc_user_list);
        nickList.setCacheColorHint(0x00000000);
        nickList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long arg3) {
                if (move) return;
                RosterItem item = (RosterItem) parent.getItemAtPosition(position);
                if (item.isEntry()) {
                    String separator = prefs.getString("nickSeparator", ", ");

                    String nick = item.getName();
                    String text = messageInput.getText().toString();
                    if (text.length() > 0) {
                        text += " " + nick + separator;
                    } else {
                        text = nick + separator;
                    }
                    messageInput.setText(text);
                    messageInput.setSelection(messageInput.getText().length());
                } else {
                    service.setSidebarMode("chats");
                    updateChats();
                }
            }
        });
        nickList.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                if (move) return false;
                RosterItem item = (RosterItem) parent.getItemAtPosition(position);
                if (item.isEntry()) {
                    String nick = item.getName();
                    MucDialogs.userMenu(Chat.this, account, jid, nick);
                    return true;
                } else return false;
            }
        });

        sendButton  = (ImageButton)findViewById(R.id.SendButton);
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(this);
        sendButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isPrivate && !isMuc) {
                    String message = messageInput.getText().toString();
                    new SendToResourceDialog(Chat.this, account, jid, message).show();
                }
                return true;
            }
        });

        messageInput = (EditText)findViewById(R.id.messageInput);
        if (!prefs.getBoolean("NoMaxLines", true)) messageInput.setMaxLines(3);
        messageInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    onClick(sendButton);
                    return true;
                }
                return false;
            }
        });

        ImageView smileImage = (ImageView) findViewById(R.id.smileImage);
        smileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                smiles.showDialog();
            }
        });
        smileImage.setVisibility(prefs.getBoolean("ShowSmiles", true) ? View.VISIBLE : View.GONE);

        if (prefs.getBoolean("SendOnEnter", false)) {
            messageInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
            messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            sendButton.setVisibility(View.GONE);
        } else {
            messageInput.setImeOptions(EditorInfo.IME_ACTION_NONE);
            messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            sendButton.setVisibility(View.VISIBLE);
        }

        if (prefs.getBoolean("SmileButtonOnKeyboard", false)) {
            messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            sendButton.setVisibility(View.VISIBLE);
        }

        attachPanel = (LinearLayout) findViewById(R.id.attachPanel);
        attachPath = (TextView) findViewById(R.id.attachPath);
        removeAttach = (ImageView) findViewById(R.id.attachRemove);
        removeAttach.setOnClickListener(this);

        if (getIntent().getBooleanExtra("file", false)) onActivityResult(REQUEST_FILE, RESULT_OK, getIntent());

        sidebar = (LinearLayout) findViewById(R.id.sidebar);
        int width = prefs.getInt("SideBarSize", 100);
        ViewGroup.LayoutParams lp = sidebar.getLayoutParams();
        lp.width = width;
        sidebar.setLayoutParams(lp);
        if (prefs.getBoolean("ShowSidebar", true)) {
            sidebar.setVisibility(View.VISIBLE);
        } else {
            sidebar.setVisibility(View.GONE);
        }

        View.OnTouchListener onTouchListener = new View.OnTouchListener() {
            int firstY = 0;
            int firstX = 0;
            boolean lock = false;

            @Override
            public boolean onTouch(View view, MotionEvent event) {

                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                int displayWidht = metrics.widthPixels;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        firstX = (int) event.getRawX();
                        firstY = (int) event.getRawY();
                        break;
                    case MotionEvent.ACTION_UP:
                        lock = false;
                        updateChats();
                        updateUsers();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(200);
                                    move = false;
                                } catch (InterruptedException ignored) { }
                            }
                        }).start();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // Detect vertical scroll
                        int nowY = (int) event.getRawY();
                        int offsetY = nowY - firstY;
                        if (Math.abs(offsetY) > 32 && !move) {
                            lock = true;
                            return false;
                        }

                        // Horizontal scroll
                        int nowX = (int) event.getRawX();
                        int offsetX = nowX - firstX;
                        if (Math.abs(offsetX) > 32 || move) {
                            if (lock) return false;
                            move = true; // block OnClickListener and OnLongClickListener
                            ViewGroup.LayoutParams lp = sidebar.getLayoutParams();
                            int lastSize = lp.width;
                            int newSize = lastSize - offsetX;
                            if (newSize < 72) newSize = 72;
                            if (newSize > displayWidht) newSize = displayWidht - 72;
                            lp.width = newSize;
                            sidebar.setLayoutParams(lp);
                            firstX = nowX;
                        }
                        break;
                }
                return move;
            }
        };
        chatsList.setOnTouchListener(onTouchListener);
        nickList.setOnTouchListener(onTouchListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        compose = false;
        jid = getIntent().getStringExtra("jid");
        account = getIntent().getStringExtra("account");

        if (service.getConferencesHash(account).containsKey(jid)) {
            isMuc = true;
            muc = service.getConferencesHash(account).get(jid);
            if (prefs.getBoolean("ShowInputHints", true)) messageInput.setHint(getString(R.string.From) + " " + StringUtils.parseName(account));

            String group = listMucAdapter.getGroup();
            if (listView.getAdapter() instanceof ChatAdapter) {
                listView.setAdapter(listMucAdapter);
                listView.setScroll(true);
            }
            else {
                if (group != null && group.equals(jid)) listView.setScroll(false); else listView.setScroll(true);
            }
        } else {
            isMuc = false;
            muc = null;
            resource = StringUtils.parseResource(jid);

            if (!service.getConferencesHash(account).containsKey(StringUtils.parseBareAddress(jid))) {
                jid = StringUtils.parseBareAddress(jid);
                isPrivate = false;
            } else isPrivate = true;

            if (resource == null || resource.equals("")) resource = service.getResource(account, jid);

            if (prefs.getBoolean("ShowInputHints", true)) {
                if (resource != null && !resource.equals("")) {
                    messageInput.setHint(getString(R.string.To) + " " + resource + " " + getString(R.string.From) + " " + StringUtils.parseName(account));
                } else messageInput.setHint(getString(R.string.From) + " " + StringUtils.parseName(account));
            }

            String j = listAdapter.getJid();
            listAdapter.update(account, jid, searchString, viewMode);
            if (listView.getAdapter() instanceof MucChatAdapter) {
                listView.setAdapter(listAdapter);
                listView.setScroll(true);
            }
            else {
                if (j != null && j.equals(jid)) listView.setScroll(false); else listView.setScroll(true);
            }

            if (!service.getActiveChats(account).contains(jid)) {
                service.addActiveChat(account, jid);
            }
        }

        service.setCurrentJid(jid);
        service.removeUnreadMesage(account, jid);
        service.removeHighlight(account, jid);

        usersAdapter = new MucUserAdapter(this, account, jid, sidebar.getLayoutParams().width);
        nickList.setAdapter(usersAdapter);
        chatsList.setAdapter(chatsAdapter);

        messageInput.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 0) {
                    if (!isMuc) {
                        if (!compose) {
                            compose = true;
                            service.setChatState(account, jid, ChatState.composing);
                        }
                    }
                    sendButton.setEnabled(service.isAuthenticated(account));
                } else {
                    if (!isMuc) {
                        if (compose) {
                            compose = false;
                            service.setChatState(account, jid, ChatState.active);
                        }
                    }
                    sendButton.setEnabled(false);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });

        messageInput.setText(service.getText(jid));
        messageInput.setSelection(messageInput.getText().length());

        if (service.isAuthenticated()) Notify.updateNotify();
        else Notify.offlineNotify(this, service.getGlobalState());
        Notify.cancelNotify(this, account, jid);

        updateChats();
        updateUsers();
        updateStatus();

        registerReceivers();
        service.resetTimer();

        if (!isMuc) service.setChatState(account, jid, ChatState.active);
        createOptionMenu();

        int position = chatsSpinnerAdapter.getPosition(account, jid);
        getActionBar().setSelectedNavigationItem(position);
        rosterItem = chatsSpinnerAdapter.getItem(position);

        if (searchString.length() > 0) {
            if (menu != null) {
                MenuItem item = menu.findItem(R.id.search);
                item.expandActionView();
            }
        }

        unreadMessages = service.getMessagesCount(account, jid);
        if (unreadMessages > 0) separatorPosition = 0;
        updateList();
        if (service.getMessageList(account, jid).isEmpty()) loadStory(false);
        if (account.equals(jid)) {
            service.removeMessagesCountForJid(account, jid);
        }
        service.removeMessagesCount(account, jid);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceivers();
        compose = false;
        if (!isMuc)  {
            service.setChatState(account, jid, ChatState.active);
            service.setResource(account, jid, resource);
            if (service.getMessageList(account, jid).isEmpty()) service.removeActiveChat(account, jid);
        }
        service.setCurrentJid("me");
        service.setText(jid, messageInput.getText().toString());
        if (!listView.isScroll()) service.addLastPosition(jid, listView.getFirstVisiblePosition());

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("SideBarSize", sidebar.getLayoutParams().width);
        editor.commit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (service.getMessageList(account,jid).isEmpty()) {
            if (!isMuc) service.setChatState(account, jid, ChatState.gone);
        }
//        msgList.clear();
        jid = null;
        account = null;
    }

    @Override
    public boolean onKeyUp(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_SEARCH) {
            MenuItem item = menu.findItem(R.id.search);
            item.expandActionView();
        }
        return super.onKeyUp(key, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        createOptionMenu();
        return true;
    }

    private void createOptionMenu() {
        if (menu != null) {
            menu.clear();
            final MenuInflater inflater = getMenuInflater();

            if (viewMode == ChatAdapter.ViewMode.multi) {
                inflater.inflate(R.menu.select_messages, menu);
                super.onCreateOptionsMenu(menu);
            } else {
                if (isMuc) inflater.inflate(R.menu.muc_chat, menu);
                else {
                    inflater.inflate(R.menu.chat, menu);
                    menu.findItem(R.id.resource).setVisible(!isPrivate);
                    try {
                        SipManager manager = service.getSipManager(account);
                        if (manager != null && manager.isRegistered("sip:"+account)) {
                            menu.findItem(R.id.call).setVisible(true);
                        }
                    } catch (SipException se) {
                        menu.findItem(R.id.call).setVisible(false);
                    }
                }

                MenuItem.OnActionExpandListener listener = new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        searchString = "";
                        updateList();
                        createOptionMenu();
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }
                };

                SearchView searchView = new SearchView(this);
                searchView.setQueryHint(getString(android.R.string.search_go));
                searchView.setSubmitButtonEnabled(false);
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextChange(String newText) {
                        return true;
                    }
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        searchString = query;
                        updateList();
                        return true;
                    }
                });

                MenuItem item = menu.findItem(R.id.search);
                item.setActionView(searchView);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
                item.setOnActionExpandListener(listener);

                super.onCreateOptionsMenu(menu);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sidebar:
                SharedPreferences.Editor editor = prefs.edit();
                if (sidebar.getVisibility() == View.GONE) {
                    sidebar.setVisibility(View.VISIBLE);
                    editor.putBoolean("ShowSidebar", true);
                } else {
                    sidebar.setVisibility(View.GONE);
                    editor.putBoolean("ShowSidebar", false);
                }
                editor.commit();
                updateChats();
                updateUsers();
                break;
            case R.id.imgur:
                Intent fIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fIntent.setType("image/*");
                fIntent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(fIntent, getString(R.string.SelectFile)), REQUEST_PHOTO);
                break;
            case android.R.id.home:
                startActivity(new Intent(this, RosterActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish();
                break;
            case R.id.nick:
                final UsersAdapter adapter = new UsersAdapter(this, account, jid);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.PasteNick);
                builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Chat.this);
                        String separator = prefs.getString("nickSeparator", ", ");
                        String nick = adapter.getItem(which).getName();

                        messageInput.setText(messageInput.getText() + nick + separator);
                        messageInput.setSelection(messageInput.getText().length());
                    }
                });
                builder.create().show();
                break;
            case R.id.subj:
                Intent sIntent = new Intent(this, SubjectActivity.class);
                sIntent.putExtra("account", account);
                sIntent.putExtra("jid", jid);
                startActivity(sIntent);
                break;
            case R.id.templates:
                startActivityForResult(new Intent(this, TemplatesActivity.class), REQUEST_TEMPLATES);
                break;
            case R.id.resource:
                final List<String> list = new ArrayList<String>();
                list.add("Auto");
                Iterator<Presence> it =  service.getRoster(account).getPresences(jid);
                while (it.hasNext()) {
                    Presence p = it.next();
                    if (p.isAvailable()) list.add(StringUtils.parseResource(p.getFrom()));
                }

                CharSequence[] array = new CharSequence[list.size()];
                for (int i = 0; i < list.size(); i++) array[i] = list.get(i);

                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.SelectResource);
                b.setItems(array, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String res = "" ;
                        if (which > 0) {
                            res = list.get(which);
                        }
                        if (res.length() > 0) res = jid + "/" + res;
                        else res = jid;
                        resource = "";
                        Intent intent = new Intent();
                        intent.putExtra("account", account);
                        intent.putExtra("jid", res);
                        setIntent(intent);
                        onPause();
                        onResume();
                    }
                });
                b.create().show();
                break;
            case R.id.info:
                Intent infoIntent = new Intent(this, VCardActivity.class);
                infoIntent.putExtra("account", account);
                infoIntent.putExtra("jid", jid);
                startActivity(infoIntent);
                break;
            case R.id.file:
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.setType("*/*");
                fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(fileIntent, getString(R.string.SelectFile)), REQUEST_FILE);
                break;
            case R.id.invite:
                MucDialogs.inviteDialog(this, account, jid);
                break;
            case R.id.history:
                loadStory(true);
                break;
            case R.id.delete_history:
                service.setMessageList(account, jid, new ArrayList<MessageItem>());
                new Thread() {
                    public void run() {
                        Chat.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                getContentResolver().delete(JTalkProvider.CONTENT_URI, "jid = '" + jid + "'", null);
                                updateList();
                            }
                        });
                    }
                }.start();
                break;
            case R.id.export_history:
                Intent export = new Intent(this, ExportActivity.class);
                export.putExtra("jid", jid);
                startActivity(export);
                break;
            case R.id.chats:
                ChangeChatDialog.show(this);
                break;
            case R.id.clear:
                clearChat();
                break;
            case R.id.close:
                closeChat();
                break;
            case R.id.leave:
                finish();
                service.leaveRoom(account, jid);
                break;
            case R.id.search:
                if (!item.isActionViewExpanded()) {
                    menu.removeItem(R.id.sidebar);
                    menu.removeItem(R.id.smile);
                    item.expandActionView();
                }
                break;
            case R.id.select:
                viewMode = ChatAdapter.ViewMode.multi;
                createOptionMenu();
                updateList();
                break;
            case R.id.copy:
                if (listView.getAdapter() instanceof ChatAdapter) listAdapter.copySelectedMessages();
                else if (listView.getAdapter() instanceof MucChatAdapter) listMucAdapter.copySelectedMessages();
                break;
            case R.id.finish:
                viewMode = ChatAdapter.ViewMode.single;
                createOptionMenu();
                updateList();
                break;
            case R.id.add_bookmark:
                BookmarksDialogs.AddDialog(this, account, jid, StringUtils.parseName(jid));
                break;
            case R.id.call:
                Intent callIntent = new Intent(this, CallActivity.class);
                callIntent.putExtra("account", account);
                callIntent.putExtra("jid", jid);
                startActivity(callIntent);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQUEST_TEMPLATES) {
            String text = data.getStringExtra("text");
            String oldtext = service.getText(jid);
            if (oldtext.length() > 0) text = oldtext + " " + text;
            service.setText(jid, text);
        } else {
            Uri uri = data.getData();
            if (uri == null) return;
            imgur = requestCode == REQUEST_PHOTO;
            attachPath.setText(uri.toString());
            attachPanel.setVisibility(View.VISIBLE);
            service.setText(jid, messageInput.getText().toString() + " ");
        }
    }

    @Override
    public void onClick(View v) {
        if (v == sendButton) {
            String attach = attachPath.getText().toString();
            if (attach.isEmpty()) {
                if (messageInput.getText().length() > 0) sendMessage();
            } else {
                Uri uri = Uri.parse(attach);
                if (uri == null) return;
                String text = messageInput.getText().toString();
                if (imgur) {
                    String j = jid;
                    if (isPrivate) j = jid;
                    else if (resource != null && resource.length() > 0) j = jid + "/" + resource;
                    new ImgurUploadTask(uri, this, account, j, text, muc).execute();
                } else {
                    String j = null;
                    String r = resource;
                    if (r != null && r.length() > 0) j = jid + "/" + resource;
                    else {
                        Presence presence = service.getRoster(account).getPresence(jid);
                        if (presence != null) j = jid + "/" + StringUtils.parseResource(presence.getFrom());
                    }
                    if (j != null) new SendFileTask(this, account, j, text, uri).execute();
                }
                removeAttach.callOnClick();
            }

            service.resetTimer();
            messageInput.setText("");
            if (prefs.getBoolean("HideKeyboard", true)) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0, null);
            }
        } else if (v == removeAttach) {
            attachPanel.setVisibility(View.GONE);
            attachPath.setText("");
            if (messageInput.getText().length() < 1) sendButton.setEnabled(false);
        }
    }

//    private void updateMessage(String id, String body) {
//        for (MessageItem item : msgList) {
//            if (item.getType() == MessageItem.Type.message) {
//                if (id.equals(item.getId())) {
//                    item.setBody(body);
//                    item.setEdited(true);
//                    listAdapter.notifyDataSetChanged();
//                }
//            }
//        }
//    }

    private void updateList() {
        boolean scroll = listView.isScroll();
        if (isMuc) {
            listMucAdapter.update(account, jid, muc.getNickname(), searchString, viewMode);
            listMucAdapter.notifyDataSetChanged();
        } else {
            listAdapter.update(account, jid, searchString, viewMode);
            listAdapter.notifyDataSetChanged();
        }

        try {
            if (unreadMessages > 0 && separatorPosition == 0) {
                separatorPosition = listView.getCount() - unreadMessages;
            }

            if (separatorPosition > 0 && separatorPosition < listView.getCount()) {
                MessageItem item = new MessageItem(null, null);
                item.setType(MessageItem.Type.separator);
                if (!isMuc) listAdapter.insert(item, separatorPosition);
                else listMucAdapter.insert(item, separatorPosition);
            }
        } catch (Exception ignored) {}

        if (prefs.getBoolean("AutoScroll", true)) {
            if (scroll && listView.getCount() >= 1) {
                listView.setSelection(listView.getCount());
            }
        }
    }

    private void updateChats() {
        chatsSpinnerAdapter.update();
        chatsSpinnerAdapter.notifyDataSetChanged();

        if (sidebar.getVisibility() == View.GONE) return;
        new Thread() {
            public void run() {
                Chat.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isMuc && service.getSidebarMode().equals("users")) {
                            nickList.setVisibility(View.VISIBLE);
                            chatsList.setVisibility(View.GONE);
                        } else {
                            chatsAdapter.update(sidebar.getLayoutParams().width);
                            chatsAdapter.notifyDataSetChanged();
                            chatsList.setVisibility(View.VISIBLE);
                            nickList.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }.start();
    }

    private void updateUsers() {
        if (sidebar.getVisibility() == View.GONE) return;
        new Thread() {
            public void run() {
                Chat.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        usersAdapter.update(sidebar.getLayoutParams().width);
                        usersAdapter.notifyDataSetChanged();
                    }
                });
            }
        }.start();
    }

    private void updateStatus() {
        chatsSpinnerAdapter.notifyDataSetChanged();
        if (service != null) {
            IconPicker ip = service.getIconPicker();
            if (ip != null) {
                ActionBar ab = getActionBar();
                ab.setDisplayUseLogoEnabled(true);
                if (isMuc) ab.setLogo(ip.getMucDrawable());
                else ab.setLogo(ip.getDrawableByPresence(service.getPresence(account, jid)));
            }
        }
    }

    private void registerReceivers() {
        textReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String text = i.getExtras().getString("text");
                if (i.getBooleanExtra("jubo", false)) {
                    Intent intent = new Intent();
                    intent.putExtra("account", account);
                    intent.putExtra("jid", "juick@juick.com");
                    setIntent(intent);
                    onPause();
                    onResume();
                }

                int pos = messageInput.getSelectionEnd();
                String oldText = messageInput.getText().toString();
                String newText = oldText.substring(0, pos) + text + oldText.substring(pos);
                messageInput.setText(newText);
                messageInput.setSelection(messageInput.getText().length());
            }
        };

        msgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String user = intent.getExtras().getString("jid");
                boolean clear = intent.getBooleanExtra("clear", false);
                if (user.equals(jid)) {
                    updateList();
                    chatsSpinnerAdapter.notifyDataSetChanged();
                } else {
                    updateUsers();
                    updateChats();
                }
                if (clear) messageInput.setText("");
            }
        };

        receivedReceiver =  new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isMuc) updateList();
            }
        };

        composeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateStatus();
                updateChats();
            }
        };

        presenceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateChats();
                updateUsers();
                if (isMuc) {
                    updateList();
                    updateStatus();
                } else {
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        String j = extras.getString("jid");
                        if (j != null && jid.equals(j)) {
                            updateStatus();
                            updateList();
                        }
                    }
                }
            }
        };

        finishReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };

        registerReceiver(finishReceiver, new IntentFilter(Constants.FINISH));
        registerReceiver(textReceiver, new IntentFilter(Constants.PASTE_TEXT));
        registerReceiver(msgReceiver, new IntentFilter(Constants.NEW_MESSAGE));
        registerReceiver(receivedReceiver, new IntentFilter(Constants.RECEIVED));
        registerReceiver(composeReceiver, new IntentFilter(Constants.UPDATE));
        registerReceiver(presenceReceiver, new IntentFilter(Constants.PRESENCE_CHANGED));
    }

    private void unregisterReceivers() {
        try {
            unregisterReceiver(textReceiver);
            unregisterReceiver(finishReceiver);
            unregisterReceiver(msgReceiver);
            unregisterReceiver(receivedReceiver);
            unregisterReceiver(composeReceiver);
            unregisterReceiver(presenceReceiver);
        } catch (Exception ignored) { }
    }

    private void sendMessage() {
        String message = messageInput.getText().toString();
        if (isMuc) {
            try {
                muc.sendMessage(message);
            } catch (Exception ignored) {}
        }
        else {
            String to = jid;
            if (isPrivate) to = jid;
            else if (resource.length() > 0) to = jid + "/" + resource;
            service.sendMessage(account, to, message);
        }
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) { }
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (firstVisibleItem + visibleItemCount == totalItemCount) listView.setScroll(true);
        else listView.setScroll(false);
    }

    private void loadStory(boolean all) {
        if (isMuc) return;
        int count = 5;
        if (maxCount > 0) count = maxCount;

        Cursor cursor = getContentResolver().query(JTalkProvider.CONTENT_URI, null, "jid = '" + jid + "' AND type = 'message'", null, MessageDbHelper._ID);
        if (cursor != null && cursor.getCount() > 0) {
            if (cursor.getCount() > count && !all) {
                cursor.moveToPosition(cursor.getCount()-count);
            } else cursor.moveToFirst();

            List<MessageItem> list = new ArrayList<MessageItem>();
            do {
                String baseId = cursor.getString(cursor.getColumnIndex(MessageDbHelper._ID));
                String id = cursor.getString(cursor.getColumnIndex(MessageDbHelper.ID));
                String nick = cursor.getString(cursor.getColumnIndex(MessageDbHelper.NICK));
                String type = cursor.getString(cursor.getColumnIndex(MessageDbHelper.TYPE));
                String stamp = cursor.getString(cursor.getColumnIndex(MessageDbHelper.STAMP));
                String body = cursor.getString(cursor.getColumnIndex(MessageDbHelper.BODY));
                boolean received = Boolean.valueOf(cursor.getString(cursor.getColumnIndex(MessageDbHelper.RECEIVED)));

                MessageItem item = new MessageItem(account, jid);
                item.setBaseId(baseId);
                item.setId(id);
                item.setName(nick);
                item.setType(MessageItem.Type.valueOf(type));
                item.setTime(stamp);
                item.setBody(body);
                item.setReceived(received);

                list.add(item);
            } while (cursor.moveToNext());
            service.setMessageList(account, jid, list);
            cursor.close();
        }
        updateList();
    }

    private void clearChat() {
        service.setMessageList(account, jid, new ArrayList<MessageItem>());
        updateList();
    }

    private void closeChat() {
        clearChat();
        service.removeActiveChat(account, jid);
        finish();
    }
}
