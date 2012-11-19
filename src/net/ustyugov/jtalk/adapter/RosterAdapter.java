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
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import net.ustyugov.jtalk.*;
import net.ustyugov.jtalk.Holders.GroupHolder;
import net.ustyugov.jtalk.Holders.ItemHolder;
import net.ustyugov.jtalk.db.AccountDbHelper;
import net.ustyugov.jtalk.db.JTalkProvider;
import net.ustyugov.jtalk.service.JTalkService;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.muc.MultiUserChat;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.jtalk2.R;

public class RosterAdapter extends ArrayAdapter<RosterItem> {
	private JTalkService service;
	private Activity activity;
	private IconPicker iconPicker;
	private SharedPreferences prefs;
	private int fontSize, statusSize;
	
	public RosterAdapter(Activity activity) {
        super(activity, R.id.name);
        this.service = JTalkService.getInstance();
        this.activity = activity;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.fontSize = Integer.parseInt(activity.getResources().getString(R.string.DefaultFontSize));
		try {
			this.fontSize = Integer.parseInt(prefs.getString("RosterSize", activity.getResources().getString(R.string.DefaultFontSize)));
		} catch (NumberFormatException ignored) { }
		this.statusSize = fontSize - 4;
    }
	
	public void update() {
		this.service = JTalkService.getInstance();
		this.iconPicker = service.getIconPicker();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		boolean hideOffline = prefs.getBoolean("hideOffline", false);
		clear();
		
		Cursor cursor = service.getContentResolver().query(JTalkProvider.ACCOUNT_URI, null, AccountDbHelper.ENABLED + " = '" + 1 + "'", null, null);
		if (cursor != null && cursor.getCount() > 0) {
			cursor.moveToFirst();
			do {
				String account = cursor.getString(cursor.getColumnIndex(AccountDbHelper.JID)).trim();
				XMPPConnection connection = service.getConnection(account);

                RosterItem acc = new RosterItem(account, RosterItem.Type.account, null);
                acc.setName(account);
                add(acc);

                if (service != null && service.getRoster(account) != null && connection != null && connection.isAuthenticated() && !service.getCollapsedGroups().contains(account)) {
                    Roster roster = service.getRoster(account);

                    // Self
                    RosterItem selfgroup = new RosterItem(account, RosterItem.Type.group, null);
                    selfgroup.setName(service.getString(R.string.SelfGroup));
                    add(selfgroup);

                    if (!service.getCollapsedGroups().contains(service.getString(R.string.SelfGroup))) {
                        Iterator<Presence> it =  roster.getPresences(account);
                        while (it.hasNext()) {
                            Presence p = it.next();
                            if (p.isAvailable()) {
                                String from = p.getFrom();
                                RosterEntry entry = new RosterEntry(from, StringUtils.parseResource(from), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                                RosterItem self = new RosterItem(account, RosterItem.Type.self, entry);
                                add(self);
                            }
                        }
                    } else {
                        selfgroup.setCollapsed(true);
                    }

                    // Add conferences
                    if (!service.getConferencesHash(account).isEmpty()) {
                        RosterItem mucGroup = new RosterItem(account, RosterItem.Type.group, null);
                        mucGroup.setName(service.getString(R.string.MUC));
                        add(mucGroup);

                        if (!service.getCollapsedGroups().contains(service.getString(R.string.MUC))) {
                            Enumeration<String> groupEnum = service.getConferencesHash(account).keys();
                            while(groupEnum.hasMoreElements()) {
                                RosterItem muc = new RosterItem(account, RosterItem.Type.muc, null);
                                muc.setName(groupEnum.nextElement());
                                add(muc);
                            }
                        } else mucGroup.setCollapsed(true);
                    }

                    Collection<RosterGroup> groups = roster.getGroups();
                    for (RosterGroup group: groups) {
                        List<String> list = new ArrayList<String>();
                        Collection<RosterEntry> entrys = group.getEntries();
                        for (RosterEntry re: entrys) {
                            String jid = re.getUser();
                            Presence.Type presenceType = service.getType(account, jid);
                            if (hideOffline) {
                                if (presenceType != Presence.Type.unavailable) list.add(jid);
                            } else {
                                list.add(jid);
                            }
                        }
                        if (list.size() > 0) {
                            String name = group.getName();
                            RosterItem item = new RosterItem(account, RosterItem.Type.group, null);
                            item.setName(name);
                            add(item);
                            if (service.getCollapsedGroups().contains(name)) item.setCollapsed(true);
                            else {
                                list = SortList.sortSimpleContacts(account, list);
                                for (String jid: list) {
                                    RosterEntry re = roster.getEntry(jid);
                                    RosterItem i = new RosterItem(account, RosterItem.Type.entry, re);
                                    add(i);
                                }
                            }
                        }
                    }

                    List<String> list = new ArrayList<String>();
                    Collection<RosterEntry> entrys = roster.getUnfiledEntries();
                    for (RosterEntry re: entrys) {
                        String jid = re.getUser();
                        Presence.Type presenceType = service.getType(account, jid);
                        if (hideOffline) {
                            if (presenceType != Presence.Type.unavailable) list.add(jid);
                        } else {
                            list.add(jid);
                        }
                    }

                    if (list.size() > 0) {
                        String name = activity.getString(R.string.Nogroup);
                        RosterItem item = new RosterItem(account, RosterItem.Type.group, null);
                        item.setName(name);
                        add(item);
                        if (service.getCollapsedGroups().contains(name)) item.setCollapsed(true);
                        else {
                            list = SortList.sortSimpleContacts(account, list);
                            for (String jid: list) {
                                RosterEntry re = roster.getEntry(jid);
                                RosterItem i = new RosterItem(account, RosterItem.Type.entry, re);
                                add(i);
                            }
                        }
                    }
                } else acc.setCollapsed(true);

			} while(cursor.moveToNext());
			cursor.close();
		}
	}
	
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		RosterItem ri = getItem(position);
		String account = ri.getAccount();
		if (ri.isGroup()) {
			GroupHolder holder;
			if (convertView == null || convertView.findViewById(R.id.group_layout) == null) {
				LayoutInflater inflater = activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.group, null, false);
				
				holder = new GroupHolder();
				holder.messageIcon = (ImageView) convertView.findViewById(R.id.msg);
				holder.messageIcon.setVisibility(View.INVISIBLE);
	            holder.text = (TextView) convertView.findViewById(R.id.name);
	            holder.text.setTextSize(fontSize);
	            holder.text.setTextColor(prefs.getBoolean("DarkColors", false) ? 0xFFFFFFFF : 0xFF000000);
	            holder.state = (ImageView) convertView.findViewById(R.id.state);
	            convertView.setTag(holder);
	            convertView.setBackgroundColor(prefs.getBoolean("DarkColors", false) ? 0x77525252 : 0xEEEEEEEE);
			} else {
				holder = (GroupHolder) convertView.getTag();
			}
	        holder.text.setText(ri.getName());
            holder.messageIcon.setImageResource(R.drawable.icon_msg);
            holder.messageIcon.setVisibility(View.INVISIBLE);
			holder.state.setImageResource(ri.isCollapsed() ? R.drawable.close : R.drawable.open);
			convertView.setBackgroundColor(prefs.getBoolean("DarkColors", false) ? 0x77525252 : 0xEEEEEEEE);
			return convertView;
		} else if (ri.isAccount()) {
            Holders.AccountHolder holder;
            if (convertView == null || convertView.findViewById(R.id.avatar) == null) {
                LayoutInflater inflater = activity.getLayoutInflater();
                convertView = inflater.inflate(R.layout.account, null, false);

                holder = new Holders.AccountHolder();
                holder.avatar = (ImageView) convertView.findViewById(R.id.avatar);
                holder.jid = (TextView) convertView.findViewById(R.id.jid);
                holder.jid.setTextSize(fontSize);
                holder.jid.setTextColor(prefs.getBoolean("DarkColors", false) ? 0xFFFFFFFF : 0xFF000000);
                holder.status = (TextView) convertView.findViewById(R.id.status);
                holder.status.setTextSize(statusSize);
                holder.status.setTextColor(prefs.getBoolean("DarkColors", false) ? 0xFFBBBBBB : 0xFF555555);
                holder.state = (ImageView) convertView.findViewById(R.id.state);
                convertView.setTag(holder);
                convertView.setBackgroundColor(prefs.getBoolean("DarkColors", false) ? 0x77999999 : 0xEECCCCCC);
            } else {
                holder = (Holders.AccountHolder) convertView.getTag();
            }
            holder.jid.setText(account);
            String status = service.getState(account);
            holder.status.setText(status);
            holder.state.setVisibility(status.length() > 0 ? View.VISIBLE : View.GONE);
            holder.state.setImageResource(ri.isCollapsed() ? R.drawable.close : R.drawable.open);
            Avatars.loadAvatar(activity, account, holder.avatar);
            return convertView;
		} else if (ri.isEntry() || ri.isSelf()) {
			RosterEntry re = ri.getEntry();
			String jid = re.getUser();
			String name = re.getName();
			if (name == null || name.length() <= 0 ) name = jid;
			
			Presence presence = service.getPresence(ri.getAccount(), jid);
			String status = service.getStatus(account, jid);
			if (service.getComposeList().contains(jid)) status = service.getString(R.string.Composes);
			
			int count = service.getMessagesCount(account, jid);
			
			ItemHolder holder;
			if (convertView == null || convertView.findViewById(R.id.status_icon) == null) {
				LayoutInflater inflater = activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.entry, null, false);
				holder = new ItemHolder();
				holder.name = (TextView) convertView.findViewById(R.id.name);
				holder.name.setTextColor(prefs.getBoolean("DarkColors", false) ? 0xFFEEEEEE : 0xFF343434);
				holder.name.setTextSize(fontSize);
				
				holder.status = (TextView) convertView.findViewById(R.id.status);
				holder.status.setTextSize(statusSize);
				holder.status.setTextColor(prefs.getBoolean("DarkColors", false) ? 0xFFBBBBBB : 0xFF555555);
				
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
			
	        holder.name.setText(name);
	        if (service.getMessagesHash(account).containsKey(jid)) {
				if (service.getMessagesHash(account).get(jid).size() > 0) holder.name.setTypeface(Typeface.DEFAULT_BOLD);
			} else holder.name.setTypeface(Typeface.DEFAULT);
	        
	        if (prefs.getBoolean("ShowStatuses", false)) {
	        	holder.status.setVisibility(status.length() > 0 ? View.VISIBLE : View.GONE);
	        	holder.status.setText(status);
	        } else holder.status.setVisibility(View.GONE);
			
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
			}
	        
	        if (prefs.getBoolean("LoadAvatar", false)) {
				Avatars.loadAvatar(activity, jid, holder.avatar);
			}
	        
			if (iconPicker != null) holder.statusIcon.setImageBitmap(iconPicker.getIconByPresence(presence));
			return convertView;
		} else if (ri.isMuc()) {
			String name = ri.getName();
			
			int count = service.getMessagesCount(account, name);
			
			if(convertView == null || convertView.findViewById(R.id.status) == null) {		
				LayoutInflater inflater = activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.entry, null, false);
				
				ItemHolder holder = new ItemHolder();
				holder.name = (TextView) convertView.findViewById(R.id.name);
				holder.name.setTextSize(fontSize);
				holder.status = (TextView) convertView.findViewById(R.id.status);
				holder.status.setTextSize(statusSize);
				holder.status.setTextColor(prefs.getBoolean("DarkColors", false) ? 0xFFBBBBBB : 0xFF555555);
				holder.counter = (TextView) convertView.findViewById(R.id.msg_counter);
				holder.counter.setTextSize(fontSize);
				holder.messageIcon = (ImageView) convertView.findViewById(R.id.msg);
				holder.messageIcon.setImageBitmap(iconPicker.getMsgBitmap());
				holder.statusIcon = (ImageView) convertView.findViewById(R.id.status_icon);
				holder.statusIcon.setPadding(3, 3, 0, 0);
				holder.statusIcon.setVisibility(View.VISIBLE);
				holder.avatar = (ImageView) convertView.findViewById(R.id.contactlist_pic);
				holder.avatar.setVisibility(View.GONE);
				holder.caps = (ImageView) convertView.findViewById(R.id.caps);
				holder.caps.setVisibility(View.GONE);
				convertView.setTag(holder);
			}
			
			String subject = "";
			boolean joined = false;
			if (service.getConferencesHash(account).containsKey(name)) {
				MultiUserChat muc = service.getConferencesHash(account).get(name);
				subject = muc.getSubject();
				joined = muc.isJoined();
			}
			if (subject == null) subject = "";
			
			ItemHolder holder = (ItemHolder) convertView.getTag();
			holder.name.setTypeface(Typeface.DEFAULT);
			holder.name.setText(StringUtils.parseName(name));
			if (service.isHighlight(account, name)) holder.name.setTextColor(0xFFAA2323);
			else holder.name.setTextColor(prefs.getBoolean("DarkColors", false) ? 0xFFEEEEEE : 0xFF343434);
			
			holder.status.setText(subject);
	        holder.status.setVisibility((prefs.getBoolean("ShowStatuses", false) && subject.length() > 0) ? View.VISIBLE : View.GONE);
			
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
	        
			if (iconPicker != null) {
				if (joined) holder.statusIcon.setImageBitmap(iconPicker.getMucBitmap());
				else holder.statusIcon.setImageBitmap(iconPicker.getNoneBitmap());
			}
			return convertView;
		} else return null;
	}
}
