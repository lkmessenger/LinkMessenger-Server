/*
 * Copyright 2022 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.entities;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import org.whispersystems.textsecuregcm.controllers.AccountController;
import org.whispersystems.textsecuregcm.util.ByteArrayBase64UrlAdapter;
import org.whispersystems.textsecuregcm.util.ExactlySize;

import javax.annotation.Nullable;
import javax.validation.Valid;

public record UsernameHashResponse(
    @Valid
    @JsonSerialize(using = ByteArrayBase64UrlAdapter.Serializing.class)
    @JsonDeserialize(using = ByteArrayBase64UrlAdapter.Deserializing.class)
    @ExactlySize(AccountController.USERNAME_HASH_LENGTH)
    @Schema(description = "The hash of the confirmed username, as supplied in the request")
    byte[] usernameHash,

    @Nullable
    @Valid
    @Schema(description = "A handle that can be included in username links to retrieve the stored encrypted username")
    UsernameLinkHandle usernameLinkHandle
) {}
