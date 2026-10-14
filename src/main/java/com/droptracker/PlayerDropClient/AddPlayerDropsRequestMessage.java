package com.droptracker.PlayerDropClient;

import java.util.Collection;

public class AddPlayerDropsRequestMessage {
    public long playerHash;

    public Collection<DropHttpMessage> drops;
}
