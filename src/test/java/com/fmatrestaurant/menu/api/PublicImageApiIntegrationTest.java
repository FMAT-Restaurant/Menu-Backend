package com.fmatrestaurant.menu.api;

import static com.fmatrestaurant.menu.TestTokens.bearer;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fmatrestaurant.menu.TestTokens;
import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.domain.model.CatalogEntry;
import com.fmatrestaurant.menu.domain.model.Image;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.ImageRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * #87: the image files are public, without permissions nor token, so a browser shows them with {@code <img src>};
 * getMenuImage keeps requiring {@code menu:images:write}. No default token is added here: every request sends
 * exactly the credential it shows. Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class PublicImageApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ImageRepository imageRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	private byte[] png;

	private UUID imageId;

	private UUID entryId;

	@BeforeEach
	void setUp() throws IOException {
		entryRepository.deleteAll();
		imageRepository.deleteAll();
		png = png();
		imageId = imageRepository.save(new Image("image/png", png)).getId();
		entryId = entryRepository.save(new CatalogEntry(1L, "Hamburguesa", "Clásica", imageId, List.of())).getId();
	}

	@Test
	@DisplayName("La url de una imagen se lee sin permisos ni token")
	void theUrlIsPublic() throws Exception {
		String url = mockMvc.perform(get("/api/v1/menu/entries/" + entryId)
				.header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.ADMIN)))
				.andReturn().getResponse().getContentAsString().transform(body -> JsonPath.read(body, "$.data.image.url"));
		String path = URI.create(url).getPath();

		byte[] served = mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(header().exists(HttpHeaders.ETAG))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-cache")))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
				.andReturn().getResponse().getContentAsByteArray();

		assertArrayEquals(png, served);
	}

	@Test
	@DisplayName("Un If-None-Match vigente responde 304 y HEAD también es público")
	void conditionalAndHeadRequests() throws Exception {
		String etag = mockMvc.perform(get("/media/images/" + imageId)).andReturn().getResponse()
				.getHeader(HttpHeaders.ETAG);

		mockMvc.perform(get("/media/images/" + imageId).header(HttpHeaders.IF_NONE_MATCH, etag))
				.andExpect(status().isNotModified());
		mockMvc.perform(head("/media/images/" + imageId)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Una imagen inexistente responde 404")
	void anUnknownImageIsNotFound() throws Exception {
		mockMvc.perform(get("/media/images/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("getMenuImage sigue exigiendo menu:images:write")
	void getMenuImageStillRequiresItsPermission() throws Exception {
		String path = "/api/v1/menu/media/images/" + imageId;

		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.CATALOG)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.with("menu:images:write"))))
				.andExpect(status().isOk());
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
