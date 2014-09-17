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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import android.content.Intent;
import android.graphics.Color;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.widget.*;
import net.ustyugov.jtalk.*;
import net.ustyugov.jtalk.Holders.ItemHolder;
import net.ustyugov.jtalk.db.AccountDbHelper;
import net.ustyugov.jtalk.db.JTalkProvider;
import net.ustyugov.jtalk.service.JTalkService;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ChatState;
import org.jivesoftware.smackx.muc.MultiUserChat;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jtalk2.R;

public class ChatsSpinnerAdapter extends ArrayAdapter<RosterItem> implements SpinnerAdapter, View.OnClickListener {
	private JTalkService service;
	private SharedPreferences prefs;
	private Activity activity;
    private Spinner spinner;
	
	public ChatsSpinnerAdapter(Activity activity, Spinner spinner) {
		super(activity, R.id.name);
        this.service = JTalkService.getInstance();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.activity = activity;
        this.spinner = spinner;
    }
	
	public void update() {
		clear();
		Cursor cursor = activity.getContentResolver().query(JTalkProvider.ACCOUNT_URI, null, AccountDbHelper.ENABLED + " = '" + 1 + "'", null, null);
		if (cursor != null && cursor.getCount() > 0) {
			cursor.moveToFirst();
			do {
				String account = cursor.getString(cursor.getColumnIndex(AccountDbHelper.JID)).trim();
				XMPPConnection connection = service.getConnection(account);

                for (String jid : service.getActiveChats(account)) {
                    if (!service.getConferencesHash(account).containsKey(jid)) {
                        RosterEntry entry = null;
                        Roster roster = service.getRoster(account);
                        if (roster != null) entry = roster.getEntry(jid);
                        if (entry == null) entry = new RosterEntry(jid, jid, RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem item = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(item);
                    }
                }

				Enumeration<String> groupEnum = service.getConferencesHash(account).keys();
				while(groupEnum.hasMoreElements()) {
					String name = groupEnum.nextElement();
					RosterItem item = new RosterItem(account, RosterItem.Type.muc, null);
					item.setName(name);
					add(item);
				}
			} while (cursor.moveToNext());
			cursor.close();
		}
	}
	
	public int getPosition(String account, String jid) {
		for (int i = 0; i < getCount(); i++) {
			RosterItem item = getItem(i);
			if (item.isEntry()) {
				if (item.getAccount().equals(account) && item.getEntry().getUser().equals(jid)) return i;
			} else if (item.isMuc()) {
				if (item.getAccount().equals(account) && item.getName().equals(jid)) return i;
			}
		}
		return 0;
	}

    @Override
    public void onClick(View view) {
        spinner.performClick();
    }
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
        List<Page> list = new ArrayList<Page>();
        int current = 0;
        int j = 0;

        for (int i = 0; i < getCount(); i++) {
            RosterItem item = getItem(i);
            String account = item.getAccount();
            String jid;

            if (item.isEntry()) {
                jid = item.getEntry().getUser();
            } else jid = item.getName();

            if (jid.equals(service.getCurrentJid())) current = j;
            else j++;

            LayoutInflater vi = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = vi.inflate(R.layout.spinner_item, null);

            String name = jid;
            if (service.getConferencesHash(account).containsKey(jid)) {
                name = StringUtils.parseName(jid);
            } else if (service.getConferencesHash(account).containsKey(StringUtils.parseBareAddress(jid))) {
                name = StringUtils.parseResource(jid);
            } else {
                RosterEntry re = item.getEntry();
                if (re != null) name = re.getName();
                if (name == null || name.equals("")) name = jid;
            }

            TextView left = (TextView) v.findViewById(R.id.left);
            if (left != null) {
                if (i == 0) left.setVisibility(View.GONE);
                else left.setVisibility(View.VISIBLE);
            }

            TextView right = (TextView) v.findViewById(R.id.right);
            if (right != null) {
                if (i == getCount()-1) right.setVisibility(View.GONE);
                else right.setVisibility(View.VISIBLE);
            }

            TextView title = (TextView) v.findViewById(R.id.title);
            title.setText(name);
            if (Colors.isLight) title.setTextColor(Color.BLACK);
            else title.setTextColor(Color.WHITE);

            v.setOnClickListener(this);
            list.add(new Page(account, jid, v));
        }

        final MyPageAdapter pa = new MyPageAdapter(list);
        ViewPager vp = new ViewPager(activity);
        vp.setAdapter(pa);
        vp.setCurrentItem(current);
        vp.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {}

            @Override
            public void onPageScrollStateChanged(int i) { }

            @Override
            public void onPageSelected(final int position) {
                final String jid = pa.getItem(position).getJid();
                final String account = pa.getItem(position).getAccount();
                if (jid == null || account == null || service.getCurrentJid().equals(jid)) return;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(350);
                        } catch (Exception ignored) {}
                        activity.sendBroadcast(new Intent(Constants.CHANGE_CHAT).putExtra("account", account).putExtra("jid", jid));
                    }
                }).start();
            }
        });
        return vp;
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		IconPicker iconPicker = service.getIconPicker();
		int fontSize = Integer.parseInt(service.getResources().getString(R.string.DefaultFontSize));
		try {
			fontSize = Integer.parseInt(prefs.getString("RosterSize", service.getResources().getString(R.string.DefaultFontSize)));
		} catch (NumberFormatException ignored) { }
		int statusSize = fontSize - 4;
		
		RosterItem item = getItem(position);
		String account = item.getAccount();
		
		ItemHolder holder;
		if (convertView == null) {
			LayoutInflater inflater = activity.getLayoutInflater();
			convertView = inflater.inflate(R.layout.entry, null, false);
			holder = new ItemHolder();
			
			holder.name = (TextView) convertView.findViewById(R.id.name);
			holder.name.setTextSize(fontSize);
			holder.status = (TextView) convertView.findViewById(R.id.status);
			holder.status.setTextSize(statusSize);
			holder.status.setTextColor(Colors.SECONDARY_TEXT);
			
			holder.counter = (TextView) convertView.findViewById(R.id.msg_counter);
			holder.counter.setTextSize(fontSize);
			holder.messageIcon = (ImageView) convertView.findViewById(R.id.msg);
			holder.messageIcon.setImageBitmap(iconPicker.getMsgBitmap());
			holder.statusIcon = (ImageView) convertView.findViewById(R.id.status_icon);
			holder.statusIcon.setVisibility(View.VISIBLE);
			holder.avatar = (ImageView) convertView.findViewById(R.id.contactlist_pic);
			holder.caps = (ImageView) convertView.findViewById(R.id.caps);
			convertView.setTag(holder);
		} else {
			holder = (ItemHolder) convertView.getTag();
		}
		
		if (item.isEntry()) {
			String jid = item.getEntry().getUser();
			String status = "";
			String name = jid;

            ChatState state = service.getRoster(account).getChatState(jid);
            if (state != null && state == ChatState.composing) status = service.getString(R.string.Composes);
			else status = service.getStatus(account, jid);
			
			if (service.getConferencesHash(account).containsKey(StringUtils.parseBareAddress(jid))) {
	        	name = StringUtils.parseResource(jid);
	        } else {
	        	RosterEntry re = item.getEntry();
	            if (re != null) name = re.getName();
	            if (name == null || name.equals("")) name = jid;
	        }
			
			Presence presence = service.getPresence(account, jid);
			int count = service.getMessagesCount(account, jid);
			
	        holder.name.setText(name);
	        holder.name.setTypeface(Typeface.DEFAULT_BOLD);
	        
	        if (prefs.getBoolean("ShowStatuses", false)) {
	        	if (status != null && status.length() > 0) {
	        		holder.status.setVisibility(View.VISIBLE);
	            	holder.status.setText(status);
	        	} else {
	        		holder.status.setVisibility(View.GONE);
	        	}
	        }
			
	        if (count > 0) {
	        	holder.messageIcon.setVisibility(View.VISIBLE);
				holder.counter.setVisibility(View.VISIBLE);
				holder.counter.setText(count+"");
			} else {
				holder.messageIcon.setVisibility(View.GONE);
				holder.counter.setVisibility(View.GONE);
			}
	        
	        if (prefs.getBoolean("ShowCaps", false)) {
        		String node = service.getNode(account, jid);
    			ClientIcons.loadClientIcon(activity, holder.caps, node);
	        } else holder.caps.setVisibility(View.GONE);
	        
	        if (prefs.getBoolean("LoadAvatar", false)) Avatars.loadAvatar(activity, jid, holder.avatar);
			holder.statusIcon.setImageBitmap(iconPicker.getIconByPresence(presence));
			return convertView;
		} else if (item.isMuc()) {
			String name = item.getName();
			String subject = null;
			boolean joined = false;
			int count = service.getMessagesCount(account, name);
			
			if (service.getConferencesHash(account).containsKey(name)) {
				MultiUserChat muc = service.getConferencesHash(account).get(name);
				subject = muc.getSubject();
				joined = muc.isJoined();
			}
			
	        holder.name.setText(StringUtils.parseName(name));
	        holder.name.setTypeface(Typeface.DEFAULT_BOLD);
	        
	        if (prefs.getBoolean("ShowStatuses", false)) {
	        	if (subject != null && subject.length() > 0) {
	        		holder.status.setVisibility(View.VISIBLE);
	            	holder.status.setText(subject);
	        	} else {
	        		holder.status.setVisibility(View.GONE);
	        	}
	        }
			
	        if (count > 0) {
	        	holder.messageIcon.setVisibility(View.VISIBLE);
				holder.counter.setVisibility(View.VISIBLE);
				holder.counter.setText(count+"");
			} else {
				holder.messageIcon.setVisibility(View.GONE);
				holder.counter.setVisibility(View.GONE);
			}
	        
	        holder.caps.setVisibility(View.GONE);
	        holder.avatar.setVisibility(View.GONE);
			if (joined) holder.statusIcon.setImageBitmap(iconPicker.getMucBitmap());
			else holder.statusIcon.setImageBitmap(iconPicker.getOfflineBitmap());
			return convertView;
		}
		return null;
	}

    private class Page {
        String jid;
        String account;
        View view;

        public Page(String account, String jid, View view) {
            this.account = account;
            this.jid = jid;
            this.view = view;
        }

        public String getAccount() { return account; }
        public String getJid() { return jid; }
        public View getView() { return view; }
    }

    private class MyPageAdapter extends PagerAdapter {
        List<Page> list = new ArrayList<Page>();

        public MyPageAdapter(List<Page> list) {
            this.list = list;
        }

        public Page getItem(int position) { return list.get(position); }

        @Override
        public Object instantiateItem(View collection, int position){
            View v = list.get(position).getView();
            ((ViewPager) collection).addView(v, 0);
            return v;
        }

        @Override
        public void destroyItem(View collection, int position, Object object){
            ((ViewPager) collection).removeView((View) object);
        }

        @Override
        public int getCount() { return list.size(); }

        @Override
        public boolean isViewFromObject(View view, Object object) { return view.equals(object); }

        @Override
        public void finishUpdate(View arg0) { }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) { }

        @Override
        public Parcelable saveState() { return null; }

        @Override
        public void startUpdate(View arg0) { }
    }
}
