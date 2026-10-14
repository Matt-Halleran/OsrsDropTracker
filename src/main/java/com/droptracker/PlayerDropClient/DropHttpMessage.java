package com.droptracker.PlayerDropClient;

import java.util.UUID;

public class DropHttpMessage
{

    public DropHttpMessage(){
        dropUUID = UUID.randomUUID().toString();
    }

    public final String dropUUID;

    public int itemId;

    public String itemName;

    public String timeStamp;

    public String source;

    public String sourceId;

    public String sourceType;

    public int quantity = -1;

    public int killCount = -1;

    public long gpValue = -1;

    public boolean collectionLogCompleted = false;

    public boolean isImportant = false;
}
