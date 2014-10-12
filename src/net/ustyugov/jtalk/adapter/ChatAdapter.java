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

package net.ustyugov.jtalk.adapter;

import java.text.SimpleDateFormat;
import java.util.*;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.*;
import android.widget.*;
import net.ustyugov.jtalk.*;
import net.ustyugov.jtalk.listener.MyTextLinkClickListener;
import net.ustyugov.jtalk.service.JTalkService;
import net.ustyugov.jtalk.smiles.Smiles;
import net.ustyugov.jtalk.view.MyTextView;

import com.jtalk2.R;

import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ChatAdapter extends ArrayAdapter<MessageItem> {
    public enum ViewMode { single, multi }

	private String searchString = "";

	private SharedPreferences prefs;
	private Activity activity;
	private Smiles smiles;
    private String account;
	private String jid;
	private boolean showtime;
    private ViewMode viewMode = ViewMode.single;

	public ChatAdapter(Activity activity, Smiles smiles) {
        super(activity, R.id.chat1);
        this.activity = activity;
        this.smiles  = smiles;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.showtime = prefs.getBoolean("ShowTime", false);
    }
	
	public void update(String account, String jid, String searchString, ViewMode viewMode) {
        if (this.viewMode == ViewMode.multi && viewMode == ViewMode.multi) return;

        this.viewMode = viewMode;
        this.account = account;
		this.jid = jid;
        this.searchString = searchString;
		clear();

        String showStatusMode = prefs.getString("StatusMessagesMode", "2");

        List<MessageItem> list = JTalkService.getInstance().getMessageList(account, jid);
        for (int i = 0; i < list.size(); i++) {
            MessageItem item = list.get(i);
            MessageItem.Type type = item.getType();
            if (searchString.length() > 0) {
                String name = item.getName();
                String body = item.getBody();
                String time = createTimeString(item.getTime());

                if (type == MessageItem.Type.status || type == MessageItem.Type.connectionstatus) {
                    if (showtime) body = time + "  " + body;
                } else {
                    if (showtime) body = time + " " + name + ": " + body;
                    else body = name + ": " + body;
                }

                if (body.toLowerCase().contains(searchString.toLowerCase())) {
                    add(item);
                }
            } else {
                if (showStatusMode.equals("0")) {
                    if (type != MessageItem.Type.status && type != MessageItem.Type.connectionstatus) add(item);
                } else if (showStatusMode.equals("1")) add(item);
                else {
                    if (type != MessageItem.Type.status) add(item);
                }
            }
        }
	}
	
	public String getJid() { return this.jid; }

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		int fontSize = Integer.parseInt(activity.getResources().getString(R.string.DefaultFontSize));
        int timeSize = Integer.parseInt(activity.getResources().getString(R.string.DefaultFontSize));
		try {
			fontSize = Integer.parseInt(prefs.getString("FontSize", activity.getResources().getString(R.string.DefaultFontSize)));
            timeSize = Integer.parseInt(prefs.getString("TimeSize", activity.getResources().getString(R.string.DefaultFontSize)));
		} catch (NumberFormatException ignored) {	}

        Holders.MessageHolder holder = new Holders.MessageHolder();
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.chat_item, null, false);

            holder.linear = (LinearLayout) convertView.findViewById(R.id.chat_item);
            holder.linear.setMinimumHeight(Integer.parseInt(prefs.getString("SmilesSize", "24")));
            holder.check = (CheckBox) convertView.findViewById(R.id.check);
            holder.text = (MyTextView) convertView.findViewById(R.id.chat1);
            holder.text.setOnTextLinkClickListener(new MyTextLinkClickListener(activity, jid));
            holder.text.setTextSize(fontSize);

            convertView.setBackgroundColor(0X00000000);
            convertView.setTag(holder);
        } else {
            holder = (Holders.MessageHolder) convertView.getTag();
        }

        final MessageItem item = getItem(position);
        String subj = "";
        String body = item.getBody();
        String name = item.getName();
        MessageItem.Type type = item.getType();
        String nick = item.getName();
        boolean received = item.isReceived();
        String time = createTimeString(item.getTime());

        if (item.getSubject().length() > 0) subj = "\n" + activity.getString(R.string.Subject) + ": " + item.getSubject() + "\n";
        body = subj + body;
        
        String message;
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        message = "";
        if (type == MessageItem.Type.separator) {
            ssb.append("~ ~ ~ ~ ~");
            ssb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(Colors.HIGHLIGHT_TEXT), 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.text.setText(ssb);
        }
        else if (type == MessageItem.Type.status || type == MessageItem.Type.connectionstatus) {
        	if (showtime) message = time + "  " + body;
        	else message = body;
            ssb.append(message);
            ssb.setSpan(new ForegroundColorSpan(Colors.STATUS_MESSAGE), 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
        	int colorLength = name.length();
        	int boldLength = colorLength;
        	
        	if (showtime) {
                if (body.length() > 4 && body.startsWith("/me")) {
                    message = time + " * " + name + " " + body.substring(3);
                } else {
                    message = time + " " + name + ": " + body;
                }
        		colorLength = name.length() + time.length() + 3;
        		boldLength = name.length() + time.length() + subj.length() + 3;
        	}
        	else {
                if (body.length() > 4 && body.startsWith("/me")) {
                    message = " * " + name + " " + body.substring(3);
                    colorLength = name.length() + 3;
                    boldLength = colorLength + subj.length();
                } else {
                    message = name + ": " + body;
                }
            }
        	ssb.append(message);
        	ssb.setSpan(new ForegroundColorSpan(Colors.PRIMARY_TEXT), 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        	ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, boldLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!nick.equals(activity.getResources().getString(R.string.Me)))
            	ssb.setSpan(new ForegroundColorSpan(Colors.INBOX_MESSAGE), 0, colorLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            else {
                ssb.setSpan(new ForegroundColorSpan(Colors.OUTBOX_MESSAGE), 0, colorLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (received && prefs.getBoolean("ShowReceivedIcon", true)) {
                    Bitmap b = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ic_delivered);
                    b = Bitmap.createScaledBitmap(b, fontSize + 10, fontSize + 10, true);
                    ssb.insert(colorLength, " ");
                    ssb.setSpan(new ImageSpan(activity, b, ImageSpan.ALIGN_BOTTOM), colorLength, colorLength+1, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                }
            }
            
            if (item.isEdited()) {
                Bitmap b = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ic_edited);
                b = Bitmap.createScaledBitmap(b, fontSize + 10, fontSize + 10, true);
                ssb.append(" ");
                ssb.setSpan(new ImageSpan(activity, b), ssb.length()-1, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (type != MessageItem.Type.separator) {
            if (showtime) ssb.setSpan(new AbsoluteSizeSpan(timeSize+4), 0, time.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Search highlight
            if (searchString.length() > 0) {
                if (ssb.toString().toLowerCase().contains(searchString.toLowerCase())) {
                    int from = 0;
                    int start = -1;
                    while ((start = ssb.toString().toLowerCase().indexOf(searchString.toLowerCase(), from)) != -1) {
                        from = start + searchString.length();
                        ssb.setSpan(new BackgroundColorSpan(Colors.SEARCH_BACKGROUND), start, start + searchString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }

            holder.check.setVisibility(viewMode == ViewMode.multi ? View.VISIBLE : View.GONE);
            holder.check.setChecked(item.isSelected());
            holder.check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    item.select(b);
                    getItem(position).select(b);
                }
            });

            if (prefs.getBoolean("LoadPictures", false)) Pictures.loadPicture(activity, jid, ssb, holder.text);

            if (prefs.getBoolean("ShowSmiles", true)) {
                int startPosition = message.length() - body.length();
                ssb = smiles.parseSmiles(holder.text, ssb, startPosition, account, jid);
            }

            if (jid.equals(Constants.JUICK) || jid.equals(Constants.JUBO)) holder.text.setTextWithLinks(ssb, MyTextView.Mode.juick);
            else if (jid.equals(Constants.POINT)) holder.text.setTextWithLinks(ssb, MyTextView.Mode.point);
            else holder.text.setTextWithLinks(ssb);
        }

        return convertView;
    }
	
    private String createTimeString(String time) {
        Date d = new Date();
        java.text.DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        String currentDate = df.format(d).substring(0,10);
        if (currentDate.equals(time.substring(0,10))) return "(" + time.substring(11) + ")";
        else return "(" + time + ")";
    }

    public void copySelectedMessages() {
        String text = "";
        for(int i = 0; i < getCount(); i++) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean showtime = prefs.getBoolean("ShowTime", false);

            MessageItem message = getItem(i);
            if (message.isSelected() && message.getType() != MessageItem.Type.separator) {
                String body = message.getBody();
                String time = message.getTime();
                String name = message.getName();
                String t = "(" + time + ")";
                if (showtime) name = t + " " + name;
                text += "> " + name + ": " + body + "\n";
            }
        }

        ClipData.Item item = new ClipData.Item(text);

        String[] mimes = {"text/plain"};
        ClipData copyData = new ClipData(text, mimes, item);

        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(copyData);
        Toast.makeText(activity, R.string.MessagesCopied, Toast.LENGTH_SHORT).show();
    }
}
