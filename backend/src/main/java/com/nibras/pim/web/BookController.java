package com.nibras.pim.web;

import com.nibras.pim.domain.Book;
import com.nibras.pim.service.BookService;
import com.nibras.pim.web.dto.BookDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class BookController extends AbstractCrudController<Book, BookDto> {

    public BookController(BookService service) {
        super(service);
    }
}
