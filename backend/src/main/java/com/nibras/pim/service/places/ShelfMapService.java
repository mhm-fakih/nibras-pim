package com.nibras.pim.service.places;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shelf map: every room, its cabinets, their shelves, and the resources placed on each — what
 * the Shelf map report draws. Resources placed only as far as a cabinet or a room are listed at
 * that level; cabinets and shelves not yet put anywhere are listed under "unplaced".
 */
@Service
public class ShelfMapService {

    private final JdbcTemplate jdbc;

    public ShelfMapService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Item(long id, String title, String author, String typeName, String typeStyle, Boolean isRead) {
    }

    public record Shelf(Long id, String name, String style, List<Item> items) {
    }

    public record Cabinet(Long id, String name, String style, List<Shelf> shelves, List<Item> loose) {
    }

    public record Room(Long id, String name, String style, List<Cabinet> cabinets, List<Item> loose) {
    }

    public record ShelfMap(List<Room> rooms, int placed, int unplaced) {
    }

    public ShelfMap map() {
        Map<Long, Room> rooms = new LinkedHashMap<>();
        Map<Long, Cabinet> cabinets = new LinkedHashMap<>();
        Map<Long, Shelf> shelves = new LinkedHashMap<>();
        jdbc.query("select id, name, style from storage_room where deleted_on is null order by name", rs -> {
            long id = rs.getLong("id");
            rooms.put(id, new Room(id, rs.getString("name"), rs.getString("style"), new ArrayList<>(), new ArrayList<>()));
        });
        // A null key holds what isn't inside anything yet.
        Room nowhere = new Room(null, null, null, new ArrayList<>(), new ArrayList<>());
        jdbc.query("select id, name, style, room_id from storage_cabinet where deleted_on is null order by name", rs -> {
            long id = rs.getLong("id");
            Cabinet c = new Cabinet(id, rs.getString("name"), rs.getString("style"), new ArrayList<>(), new ArrayList<>());
            cabinets.put(id, c);
            Long roomId = (Long) rs.getObject("room_id", Long.class);
            rooms.getOrDefault(roomId, nowhere).cabinets().add(c);
        });
        Cabinet noCabinet = new Cabinet(null, null, null, new ArrayList<>(), new ArrayList<>());
        jdbc.query("select id, name, style, cabinet_id from storage_shelf where deleted_on is null order by name", rs -> {
            long id = rs.getLong("id");
            Shelf s = new Shelf(id, rs.getString("name"), rs.getString("style"), new ArrayList<>());
            shelves.put(id, s);
            Long cabId = (Long) rs.getObject("cabinet_id", Long.class);
            cabinets.getOrDefault(cabId, noCabinet).shelves().add(s);
        });
        int[] placed = {0};
        jdbc.query("""
                select b.id, b.title, b.author, b.is_read, t.name as type_name, t.style as type_style,
                       b.storage_room_id, b.storage_cabinet_id, b.storage_shelf_id
                  from book b left join resource_type t on t.id = b.type_id
                 where b.deleted_on is null
                   and (b.storage_room_id is not null or b.storage_cabinet_id is not null or b.storage_shelf_id is not null)
                 order by b.title""", rs -> {
            Item it = new Item(rs.getLong("id"), rs.getString("title"), rs.getString("author"),
                    rs.getString("type_name"), rs.getString("type_style"), (Boolean) rs.getObject("is_read", Boolean.class));
            Long shelf = (Long) rs.getObject("storage_shelf_id", Long.class);
            Long cab = (Long) rs.getObject("storage_cabinet_id", Long.class);
            Long room = (Long) rs.getObject("storage_room_id", Long.class);
            if (shelf != null && shelves.containsKey(shelf)) shelves.get(shelf).items().add(it);
            else if (cab != null && cabinets.containsKey(cab)) cabinets.get(cab).loose().add(it);
            else if (room != null && rooms.containsKey(room)) rooms.get(room).loose().add(it);
            else return;
            placed[0]++;
        });
        Integer total = jdbc.queryForObject("select count(*) from book where deleted_on is null", Integer.class);
        List<Room> out = new ArrayList<>(rooms.values());
        if (!noCabinet.shelves().isEmpty()) nowhere.cabinets().add(noCabinet);
        if (!nowhere.cabinets().isEmpty()) out.add(nowhere);
        return new ShelfMap(out, placed[0], (total == null ? 0 : total) - placed[0]);
    }
}
