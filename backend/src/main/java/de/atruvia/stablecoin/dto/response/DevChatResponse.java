package de.atruvia.stablecoin.dto.response;

import java.util.List;

public record DevChatResponse(
        String reply,
        List<String> sourceReferences
) {}
