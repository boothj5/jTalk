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

package net.ustyugov.jtalk.adapter.muc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import android.text.TextUtils;
import net.ustyugov.jtalk.*;
import net.ustyugov.jtalk.Holders.GroupHolder;
import net.ustyugov.jtalk.Holders.ItemHolder;
import net.ustyugov.jtalk.service.JTalkService;

import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.packet.MUCUser;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.jtalk2.R;

public class MucUserAdapter extends ArrayAdapter<RosterItem> {
	private String group;
	private String account;
	private Activity activity;
    private int sidebarSize = 100;
    SharedPreferences prefs;

    enum Mode { nick, status, all }
	
	public MucUserAdapter(Activity activity, String account, String group, int sidebarSize) {
		super(activity, R.id.name);
		this.activity = activity;
		this.group = group;
		this.account = account;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        update(sidebarSize);
	}
	
	public void setGroup(String group) {
		this.group = group;
	}
	
	public void update(int sidebarSize) {
        this.sidebarSize = sidebarSize;

		List<String> mOnline = new ArrayList<String>();
		List<String> mChat = new ArrayList<String>();
		List<String> mAway = new ArrayList<String>();
		List<String> mXa = new ArrayList<String>();
		List<String> mDnd = new ArrayList<String>();
		
		List<String> pOnline = new ArrayList<String>();
		List<String> pChat = new ArrayList<String>();
		List<String> pAway = new ArrayList<String>();
		List<String> pXa = new ArrayList<String>();
		List<String> pDnd = new ArrayList<String>();
		
		List<String> vOnline = new ArrayList<String>();
		List<String> vChat = new ArrayList<String>();
		List<String> vAway = new ArrayList<String>();
		List<String> vXa = new ArrayList<String>();
		List<String> vDnd = new ArrayList<String>();
		
		JTalkService service = JTalkService.getInstance();
		Roster roster = service.getRoster(account);
		XMPPConnection connection = service.getConnection(account);
		clear();

		if (group != null && service.getConferencesHash(account).containsKey(group)) {
			Iterator<Presence> it = service.getRoster(account).getPresences(group);
			while (it.hasNext()) {
				Presence p = it.next();
                if (p.getType() == Presence.Type.unavailable) continue;
				Presence.Mode m = p.getMode();
				if (m == null) m = Presence.Mode.available;
				String role = "visitor";
				String jid = p.getFrom();
	    		MUCUser mucUser = (MUCUser) p.getExtension("x", "http://jabber.org/protocol/muc#user");
	    		if (mucUser != null) {
	    			role = mucUser.getItem().getRole();
	    			if (role.equals("visitor")) {
	    				if (m == Presence.Mode.chat) vChat.add(jid);
	    				else if (m == Presence.Mode.away) vAway.add(jid);
	    				else if (m == Presence.Mode.xa) vXa.add(jid);
	    				else if (m == Presence.Mode.dnd) vDnd.add(jid);
	    				else if (m == Presence.Mode.available) vOnline.add(jid);

                        Collections.sort(vChat, new SortList.StringComparator());
                        Collections.sort(vAway, new SortList.StringComparator());
                        Collections.sort(vXa, new SortList.StringComparator());
                        Collections.sort(vDnd, new SortList.StringComparator());
                        Collections.sort(vOnline, new SortList.StringComparator());
	    			}
	    			else if (role.equals("participant")) {
	    				if (m == Presence.Mode.chat) pChat.add(jid);
	    				else if (m == Presence.Mode.away) pAway.add(jid);
	    				else if (m == Presence.Mode.xa) pXa.add(jid);
	    				else if (m == Presence.Mode.dnd) pDnd.add(jid);
	    				else if (m == Presence.Mode.available) pOnline.add(jid);

                        Collections.sort(pChat, new SortList.StringComparator());
                        Collections.sort(pAway, new SortList.StringComparator());
                        Collections.sort(pXa, new SortList.StringComparator());
                        Collections.sort(pDnd, new SortList.StringComparator());
                        Collections.sort(pOnline, new SortList.StringComparator());
	    			}
	    			else if (role.equals("moderator")) {
	    				if (m == Presence.Mode.chat) mChat.add(jid);
	    				else if (m == Presence.Mode.away) mAway.add(jid);
	    				else if (m == Presence.Mode.xa) mXa.add(jid);
	    				else if (m == Presence.Mode.dnd) mDnd.add(jid);
	    				else if (m == Presence.Mode.available) mOnline.add(jid);

                        Collections.sort(mChat, new SortList.StringComparator());
                        Collections.sort(mAway, new SortList.StringComparator());
                        Collections.sort(mXa, new SortList.StringComparator());
                        Collections.sort(mDnd, new SortList.StringComparator());
                        Collections.sort(mOnline, new SortList.StringComparator());
	    			}
	    		}
			}
			
			int mCount = mOnline.size() + mAway.size() + mXa.size() + mDnd.size() + mChat.size();
			int pCount = pOnline.size() + pAway.size() + pXa.size() + pDnd.size() + pChat.size();
			int vCount = vOnline.size() + vAway.size() + vXa.size() + vDnd.size() + vChat.size();
			
			if (mCount > 0) {
				RosterItem item = new RosterItem(account, RosterItem.Type.group, null);
				item.setName(mCount+"");
				item.setObject("moderator");
				add(item);
                if (!prefs.getBoolean("SortByStatuses", true)) {
                    List<String> moderators = new ArrayList<String>();
                    moderators.addAll(mOnline);
                    moderators.addAll(mChat);
                    moderators.addAll(mAway);
                    moderators.addAll(mXa);
                    moderators.addAll(mDnd);
                    Collections.sort(moderators, new SortList.StringComparator());

                    for (String jid : moderators) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                } else {
                    for (String jid : mChat) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : mOnline) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : mAway) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : mXa) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : mDnd) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                }
			}
			
			if (pCount > 0) {
				RosterItem item = new RosterItem(account, RosterItem.Type.group, null);
				item.setName(pCount+"");
				item.setObject("participant");
				add(item);
                if (!prefs.getBoolean("SortByStatuses", true)) {
                    List<String> participants = new ArrayList<String>();
                    participants.addAll(pOnline);
                    participants.addAll(pChat);
                    participants.addAll(pAway);
                    participants.addAll(pXa);
                    participants.addAll(pDnd);
                    Collections.sort(participants, new SortList.StringComparator());

                    for (String jid : participants) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                } else {
                    for (String jid : pChat) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : pOnline) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : pAway) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : pXa) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : pDnd) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                }
			}
			
			if (vCount > 0) {
				RosterItem item = new RosterItem(account, RosterItem.Type.group, null);
				item.setName(vCount+"");
				item.setObject("visitor");
				add(item);
                if (!prefs.getBoolean("SortByStatuses", true)) {
                    List<String> visitors = new ArrayList<String>();
                    visitors.addAll(vOnline);
                    visitors.addAll(vChat);
                    visitors.addAll(vAway);
                    visitors.addAll(vXa);
                    visitors.addAll(vDnd);
                    Collections.sort(visitors, new SortList.StringComparator());

                    for (String jid : visitors) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                } else {
                    for (String jid : vChat) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : vOnline) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : vAway) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : vXa) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                    for (String jid : vDnd) {
                        RosterEntry entry = new RosterEntry(jid, StringUtils.parseResource(jid), RosterPacket.ItemType.both, RosterPacket.ItemStatus.SUBSCRIPTION_PENDING, roster, connection);
                        RosterItem i = new RosterItem(account, RosterItem.Type.entry, entry);
                        add(i);
                    }
                }
			}
		}
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
        Mode viewMode = Mode.nick;
        if (sidebarSize > 3 * (48 * activity.getResources().getDisplayMetrics().density)) {
            viewMode = Mode.all;
        } else if (sidebarSize > 2 * (48 * activity.getResources().getDisplayMetrics().density)) {
            viewMode = Mode.status;
        }

		JTalkService service = JTalkService.getInstance();
		IconPicker ip = service.getIconPicker();
		int fontSize = Integer.parseInt(service.getResources().getString(R.string.DefaultFontSize));
		try {
			fontSize = Integer.parseInt(prefs.getString("RosterSize", service.getResources().getString(R.string.DefaultFontSize)));
		} catch (NumberFormatException ignored) { }
		
		RosterItem item = getItem(position);
		if (item.isGroup()) {
			GroupHolder holder;
			if (convertView == null || convertView.getTag() == null || convertView.getTag() instanceof ItemHolder) {
				LayoutInflater inflater = activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.group, null, false);
				
				holder = new GroupHolder();
				holder.messageIcon = (ImageView) convertView.findViewById(R.id.msg);
                holder.messageIcon.setVisibility(View.GONE);

	            holder.text = (TextView) convertView.findViewById(R.id.name);
	            holder.text.setTextSize(fontSize-2);
	            holder.text.setTextColor(Colors.PRIMARY_TEXT);
                holder.text.setEllipsize(TextUtils.TruncateAt.MIDDLE);

	            holder.state = (ImageView) convertView.findViewById(R.id.state);
	            holder.state.setVisibility(View.GONE);
	            convertView.setTag(holder);
	            convertView.setBackgroundColor(Colors.GROUP_BACKGROUND);
			} else {
				holder = (GroupHolder) convertView.getTag();
			}
	        holder.text.setText(item.getName());

            String role = (String) item.getObject();
            if (role.equals("moderator")) holder.text.setText(activity.getString(R.string.Moderators) + " " + item.getName());
            else if (role.equals("participant")) holder.text.setText(activity.getString(R.string.Participants) + " " + item.getName());
            else holder.text.setText(activity.getString(R.string.Visitors) + " " + item.getName());

			return convertView;
		} else if (item.isEntry()) {
			String name = item.getName();
			String jid = item.getEntry().getUser();
			if (name == null || name.length() <= 0 ) name = jid;
            int count = service.getMessagesCount(account, jid);
			Presence presence = service.getPresence(item.getAccount(), jid);

            ItemHolder holder = new ItemHolder();
			if(convertView == null || convertView.getTag() == null || convertView.getTag() instanceof GroupHolder) {
				LayoutInflater inflater = activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.entry, null, false);

				holder.name = (TextView) convertView.findViewById(R.id.name);
				holder.name.setTextSize(fontSize);
				holder.status = (TextView) convertView.findViewById(R.id.status);
                holder.status.setTextSize(fontSize-4);
				holder.status.setVisibility(View.GONE);
				holder.counter = (TextView) convertView.findViewById(R.id.msg_counter);
				holder.counter.setTextSize(fontSize);
				holder.messageIcon = (ImageView) convertView.findViewById(R.id.msg);
				holder.messageIcon.setImageBitmap(ip.getMsgBitmap());
				holder.statusIcon = (ImageView) convertView.findViewById(R.id.status_icon);
				holder.statusIcon.setPadding(3, 3, 0, 0);
				holder.statusIcon.setVisibility(View.VISIBLE);
				holder.avatar = (ImageView) convertView.findViewById(R.id.contactlist_pic);
				holder.caps = (ImageView) convertView.findViewById(R.id.caps);
				convertView.setTag(holder);
			} else {
                holder = (ItemHolder) convertView.getTag();
            }

			holder.name.setText(name);
			if (service.getComposeList().contains(jid)) holder.name.setTextColor(Colors.HIGHLIGHT_TEXT);
			else holder.name.setTextColor(Colors.PRIMARY_TEXT);

			if (service.getActiveChats(account).contains(jid)) {
				holder.name.setTypeface(Typeface.DEFAULT_BOLD);
			} else {
                holder.name.setTypeface(Typeface.DEFAULT);
            }
			
	        if (count > 0) {
	        	holder.messageIcon.setVisibility(View.VISIBLE);
				holder.counter.setVisibility(View.VISIBLE);
				holder.counter.setText(count+"");
			} else {
                holder.messageIcon.setVisibility(View.GONE);
                holder.counter.setVisibility(View.GONE);
			}

            String statusText = service.getStatus(account, jid);
            if (service.getComposeList().contains(jid)) statusText = service.getString(R.string.Composes);
            if (prefs.getBoolean("StatusInBar", true)) {
                holder.status.setVisibility(statusText.length() > 0 ? View.VISIBLE : View.GONE);
                holder.status.setText(statusText);
            } else holder.status.setVisibility(View.GONE);

            MUCUser mucUser = (MUCUser) presence.getExtension("x", "http://jabber.org/protocol/muc#user");
            if (mucUser != null) {
                String affiliation = mucUser.getItem().getAffiliation();
                if (affiliation != null) {
                    if (affiliation.equals("admin")) holder.name.setTextColor(Colors.AFFILIATION_ADMIN);
                    else if (affiliation.equals("owner")) holder.name.setTextColor(Colors.AFFILIATION_OWNER);
                    else if (affiliation.equals("member")) holder.name.setTextColor(Colors.AFFILIATION_MEMBER);
                    else holder.name.setTextColor(Colors.AFFILIATION_NONE);
                }
            }

            if (viewMode == Mode.nick) {
                holder.statusIcon.setVisibility(View.GONE);
                holder.caps.setVisibility(View.GONE);
                holder.avatar.setVisibility(View.GONE);
            } else {
                if (ip != null) {
                    holder.statusIcon.setImageBitmap(ip.getIconByPresence(presence));
                    holder.statusIcon.setVisibility(View.VISIBLE);
                }
                if (viewMode == Mode.all) {
                    if (prefs.getBoolean("ShowCaps", false)) {
                        String node = service.getNode(account, jid);
                        ClientIcons.loadClientIcon(activity, holder.caps, node);
                    }

                    if (prefs.getBoolean("LoadAvatar", false)) {
                        Avatars.loadAvatar(activity, jid.replaceAll("/", "%"), holder.avatar);
                    }
                } else {
                    holder.caps.setVisibility(View.GONE);
                    holder.avatar.setVisibility(View.GONE);
                }
            }
			return convertView;
		}
        return null;
    }
}
