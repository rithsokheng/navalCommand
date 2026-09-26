package com.battleship.net;

import com.battleship.model.CellStatus;
import com.battleship.model.Coordinate;
import com.battleship.model.Orientation;
import com.battleship.model.ShipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetMessageCodecTest {

    private NetMessageCodec codec;

    @BeforeEach
    void setUp() {
        codec = new NetMessageCodec();
    }

    @Test
    void roundTripHello() {
        NetMessage.Hello original = new NetMessage.Hello("9482");
        String json = codec.encode(original);
        NetMessage decoded = codec.decode(json);

        assertInstanceOf(NetMessage.Hello.class, decoded);
        assertEquals("9482", ((NetMessage.Hello) decoded).code());
    }

    @Test
    void roundTripWelcome() {
        NetMessage.Welcome original = new NetMessage.Welcome("FLEET_ACTION");
        String json = codec.encode(original);
        NetMessage decoded = codec.decode(json);

        assertInstanceOf(NetMessage.Welcome.class, decoded);
        assertEquals("FLEET_ACTION", ((NetMessage.Welcome) decoded).theater());
    }

    @Test
    void roundTripReject() {
        NetMessage.Reject original = new NetMessage.Reject("Wrong join code");
        String json = codec.encode(original);
        NetMessage decoded = codec.decode(json);

        assertInstanceOf(NetMessage.Reject.class, decoded);
        assertEquals("Wrong join code", ((NetMessage.Reject) decoded).reason());
    }

    @Test
    void roundTripReady() {
        NetMessage.Ready original = new NetMessage.Ready();
        String json = codec.encode(original);
        NetMessage decoded = codec.decode(json);

        assertInstanceOf(NetMessage.Ready.class, decoded);
    }

    @Test
    void roundTripStart() {
        NetMessage.Start original = new NetMessage.Start("CLIENT");
        String json = codec.encode(original);
        NetMessage decoded = codec.decode(json);

        assertInstanceOf(NetMessage.Start.class, decoded);
        assertEquals("CLIENT", ((NetMessage.Start) decoded).firstPlayer());
    }

    @Test
    void roundTripFire() {
        NetMessage.Fire original = new NetMessage.Fire("standard", new Coordinate(4, 7), Orientation.VERTICAL);
        String json = codec.encode(original);
        NetMessage decoded = codec.decode(json);

        assertInstanceOf(NetMessage.Fire.class, decoded);
        NetMessage.Fire fire = (NetMessage.Fire) decoded;
        assertEquals("standard", fire.weaponId());
        assertEquals(new Coordinate(4, 7), fire.anchor());
        assertEquals(Orientation.VERTICAL, fire.orientation());
    }

    @Test
    void roundTripFireResult() {
        List<NetMessage.CellResult> cells = List.of(
                new NetMessage.CellResult(new Coordinate(2, 3), CellStatus.HIT),
                new NetMessage.CellResult(new Coordinate(2, 4), CellStatus.MISS)
        );
        List<NetMessage.SunkShipInfo> sunk = List.of(
                new NetMessage.SunkShipInfo(ShipType.PATROL_BOAT, List.of(new Coordinate(1, 1), new Coordinate(1, 2)))
        );
        NetMessage.FireResult original = new NetMessage.FireResult(cells, sunk, true);
        String json = codec.encode(original);
        NetMessage decoded = codec.decode(json);

        assertInstanceOf(NetMessage.FireResult.class, decoded);
        NetMessage.FireResult fr = (NetMessage.FireResult) decoded;
        assertTrue(fr.defenderLost());
        assertEquals(2, fr.results().size());
        assertEquals(CellStatus.HIT, fr.results().get(0).outcome());
        assertEquals(1, fr.sunkShips().size());
        assertEquals(ShipType.PATROL_BOAT, fr.sunkShips().get(0).shipType());
    }

    @Test
    void decodeInvalidJsonReturnsNull() {
        assertNull(codec.decode("not valid json"));
        assertNull(codec.decode("{}"));
        assertNull(codec.decode("{\"type\":\"UNKNOWN_TYPE\"}"));
    }
}
