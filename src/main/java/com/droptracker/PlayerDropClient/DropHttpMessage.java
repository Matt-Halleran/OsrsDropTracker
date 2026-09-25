package com.droptracker.PlayerDropClient;

import java.util.UUID;

public class DropHttpMessage
{

    public DropHttpMessage(){
        DropUUID = UUID.randomUUID().toString();
    }

    public final String DropUUID;

    public int ItemId;

    public String ItemName;

    public String TimeStamp;

    public String Source;

    public int Quantity = -1;

    public int KillCount = -1;

    public int GpValue = -1;

    public boolean CollectionLogCompleted = false;

    public boolean IsImportant = false;
}
