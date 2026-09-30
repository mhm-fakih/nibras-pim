package com.nibras.pim.service;

import com.nibras.pim.domain.Contact;
import com.nibras.pim.repo.ContactRepository;
import com.nibras.pim.web.dto.ContactDto;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contacts as a first-class module, so people can be created, edited and — crucially — bookmarked
 * from the clients.
 *
 * <p>That last one is why this exists. The phone's Contacts page selects on {@code bookmarked}, and
 * until this module there was nothing anywhere that could set the flag: the sixteen bookmarked
 * people were bookmarked in the legacy system years ago and the other 171 were unreachable.
 */
@Service
public class ContactService extends AbstractCrudService<Contact, ContactDto> {

    /**
     * Statuses that mean a task is off the list.
     *
     * <p>Done and Cancelled are finished; Deferred and Frozen are deliberately parked. Everything
     * else — including a task with NO status, since unset is not the same as finished — is work you
     * could still pick up.
     *
     * <p>Matched on the status CODE, not its display name: the names are editable in Parameters and
     * renaming "Cancelled" would otherwise quietly change what counts as open.
     *
     * <p>Defined once, here, because three things read it — the per-row count, the detail list and
     * the phone's grouped view — and a page whose count disagrees with its own list is worse than
     * either being wrong alone.
     */
    public static final Set<String> CLOSED = Set.of("done", "dismissed", "def", "froz");

    private final ContactRepository contacts;

    public ContactService(ContactRepository repo) {
        super(repo);
        this.contacts = repo;
    }

    @Override
    public String name() {
        return "contacts";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("name", "summary", "jobTitle", "telephones", "email", "description");
    }

    @Override
    protected ContactDto toDto(Contact c) {
        return new ContactDto(
                c.getId(), c.getVersion(), c.feedTitle(),
                c.getSummary(), c.getName(), c.getCode(), c.getJobTitle(),
                c.getTelephones(), c.getEmail(), c.getAddress(),
                c.getNotes(), c.getDescription(),
                c.getPriority(), c.getBookmarked(),
                c.getId() == null ? 0 : (int) contacts.countActiveTasks(c.getId(), CLOSED),
                coverExists(c.getId()), c.getDateCreated(), c.getLastUpdated());
    }

    @Override
    protected Contact toNew(ContactDto dto) {
        Contact c = new Contact();
        apply(dto, c);
        return c;
    }

    @Override
    protected void apply(ContactDto dto, Contact c) {
        // summary is NOT NULL in the legacy table — the one required column beyond the BaseEntity
        // ones. Falling back to the name keeps a contact created with only a name from failing at
        // the insert, which is what a client that does not know the schema would otherwise hit.
        String summary = dto.summary() != null && !dto.summary().isBlank()
                ? dto.summary()
                : (dto.name() != null ? dto.name() : "");
        c.setSummary(summary);
        c.setName(dto.name());
        c.setCode(dto.code());
        c.setJobTitle(dto.jobTitle());
        c.setTelephones(dto.telephones());
        c.setEmail(dto.email());
        c.setAddress(dto.address());
        c.setNotes(dto.notes());
        c.setDescription(dto.description());
        c.setPriority(dto.priority());
        c.setBookmarked(dto.bookmarked());
    }

    /**
     * None. A contact has no status, type, department or tags of its own in this table — the
     * lookup-driven fields the other modules carry simply are not there, and offering empty
     * dropdowns would be worse than offering none.
     */
    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of();
    }
}
