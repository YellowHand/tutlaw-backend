package pl.tutlaw.backend.tag;

import pl.tutlaw.backend.tag.dto.TagResponse;

public class TagMapper {
    public static TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}