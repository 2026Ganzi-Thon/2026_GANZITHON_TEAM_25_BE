package com.mongle.backend.session.dto;

import com.mongle.backend.session.EntryType;
import jakarta.validation.constraints.NotNull;

public record StartSessionRequest(@NotNull(message = "entryType은 필수입니다") EntryType entryType) {
}
