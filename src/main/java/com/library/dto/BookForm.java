package com.library.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BookForm {
    private Long id;

    @NotBlank(message = "ISBN is required") @Size(max = 30)
    private String isbn;

    @NotBlank(message = "Title is required") @Size(max = 200)
    private String title;

    @NotBlank(message = "Author is required") @Size(max = 150)
    private String author;

    @Size(max = 80)
    private String category;

    @Min(value = 1, message = "At least 1 copy is required")
    private int totalCopies = 1;
}
