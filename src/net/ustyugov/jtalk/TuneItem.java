package net.ustyugov.jtalk;

import org.jivesoftware.smackx.packet.PEPItem;

public class TuneItem extends PEPItem {
    String artist = null;
    String source = null;
    String title  = null;

    public TuneItem(String id) {
        super(id);
    }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    @Override
    public String getNode() {
        return "http://jabber.org/protocol/tune";
    }

    @Override
    public String getItemDetailsXML() {
        StringBuilder sb = new StringBuilder();
        sb.append("<tune" + " xmlns=\"" + getNode() + "\">");
        if (artist != null) sb.append("<artist>" + artist + "</artist>");
        if (title != null) sb.append("<title>" + title + "</title>");
        if (source != null) sb.append("<source>" + source + "</source>");
        sb.append("</tune>");
        return sb.toString();
    }
}
