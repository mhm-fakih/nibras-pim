package com.nibras.pim;

import com.nibras.pim.service.BookService;
import com.nibras.pim.service.LookupAdminService;
import com.nibras.pim.service.places.ShelfMapService;
import com.nibras.pim.support.AuthTestSupport;
import com.nibras.pim.web.dto.BookDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Room -> cabinet -> shelf as parameters, a resource's place, and the shelf map. */
class StoragePlacesTests extends AuthTestSupport {

    @Autowired
    LookupAdminService lookups;

    @Autowired
    BookService books;

    @Autowired
    ShelfMapService shelfMap;

    private final String tag = "sp" + (System.nanoTime() % 1_000_000);

    /** A BookDto with just a title and a place — every other component null/empty. */
    private static BookDto book(String title, Long room, Long cabinet, Long shelf) throws Exception {
        Map<String, Object> v = Map.of("title", title, "storageRoomId", room == null ? "" : room,
                "storageCabinetId", cabinet == null ? "" : cabinet, "storageShelfId", shelf == null ? "" : shelf,
                "tagIds", List.of());
        RecordComponent[] comps = BookDto.class.getRecordComponents();
        Object[] args = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            Object x = v.get(comps[i].getName());
            args[i] = "".equals(x) ? null : x;
        }
        Class<?>[] types = java.util.Arrays.stream(comps).map(RecordComponent::getType).toArray(Class<?>[]::new);
        return BookDto.class.getDeclaredConstructor(types).newInstance(args);
    }

    @Test
    void aShelfPlacesTheResourceInItsCabinetAndRoom_andMovesWithIt() throws Exception {
        var study = lookups.create("storage_room", "Study " + tag, null);
        var hall = lookups.create("storage_room", "Hall " + tag, null);
        var cabA = lookups.create("storage_cabinet", "Cabinet A " + tag, null, study.id());
        var cabB = lookups.create("storage_cabinet", "Cabinet B " + tag, null, hall.id());
        var shelf1 = lookups.create("storage_shelf", "Shelf 1 " + tag, null, cabA.id());

        // Only the shelf is given: cabinet and room follow from the parameters, even if the
        // client sent a contradicting room.
        BookDto saved = books.create(book("On shelf " + tag, hall.id(), null, shelf1.id()));
        assertThat(saved.storageShelfId()).isEqualTo(shelf1.id());
        assertThat(saved.storageCabinetId()).isEqualTo(cabA.id());
        assertThat(saved.storageRoomId()).isEqualTo(study.id());
        assertThat(saved.storageShelf().name()).isEqualTo("Shelf 1 " + tag);

        BookDto inRoom = books.create(book("In the hall " + tag, hall.id(), null, null));

        var map = shelfMap.map();
        var studyRoom = map.rooms().stream().filter(r -> study.id().equals(r.id())).findFirst().orElseThrow();
        assertThat(studyRoom.cabinets()).singleElement().satisfies(c ->
                assertThat(c.shelves()).singleElement().satisfies(s ->
                        assertThat(s.items()).extracting(ShelfMapService.Item::id).containsExactly(saved.id())));
        var hallRoom = map.rooms().stream().filter(r -> hall.id().equals(r.id())).findFirst().orElseThrow();
        assertThat(hallRoom.loose()).extracting(ShelfMapService.Item::id).containsExactly(inRoom.id());

        // Moving the shelf to the other cabinet moves what is on it.
        lookups.setParent("storage_shelf", shelf1.id(), cabB.id());
        BookDto moved = books.get(saved.id());
        assertThat(moved.storageCabinetId()).isEqualTo(cabB.id());
        assertThat(moved.storageRoomId()).isEqualTo(hall.id());

        // Removing the cabinet leaves the resource in its room, and it still opens.
        lookups.delete("storage_cabinet", cabB.id());
        BookDto after = books.get(saved.id());
        assertThat(after.storageCabinetId()).isNull();
        assertThat(after.storageShelfId()).isNull();
        assertThat(after.storageRoomId()).isEqualTo(hall.id());

        assertThat(lookups.list()).anySatisfy(g -> {
            assertThat(g.table()).isEqualTo("storage_shelf");
            assertThat(g.parentTable()).isEqualTo("storage_cabinet");
        });
    }
}
