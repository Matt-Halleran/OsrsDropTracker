package com.droptracker.PlayerDropClient;

import java.util.Collection;

public class AddPlayerDropsRequestMessage {
    public long PlayerHash;

    public Collection<DropHttpMessage> Drops;
}
