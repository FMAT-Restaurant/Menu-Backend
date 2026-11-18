package com.fmatrestaurant.menu.api;

import static com.fmatrestaurant.menu.api.ContractValidation.conformsToContract;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.fmatrestaurant.menu.TestcontainersConfiguration;
import com.fmatrestaurant.menu.application.service.CategoryService;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogEntryRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CatalogOfferRepository;
import com.fmatrestaurant.menu.infrastructure.repository.CategoryRepository;

/**
 * The responses that already follow the contract are validated against it with {@link ContractValidation}.
 * Requires Docker.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
class ContractValidationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private CatalogEntryRepository entryRepository;

	@Autowired
	private CatalogOfferRepository offerRepository;

	@BeforeEach
	void setUp() {
		offerRepository.deleteAll();
		entryRepository.deleteAll();
		categoryRepository.deleteAll();
	}

	@Test
	@DisplayName("La página de categorías (listMenuCategories) cumple el contrato")
	void categoryPageConformsToTheContract() throws Exception {
		categoryService.create("Bebidas", "Frías y calientes");
		categoryService.create("Postres", null);

		mockMvc.perform(get(ApiVersioning.BASE + "/menu/categories"))
				.andExpect(status().isOk())
				.andExpect(conformsToContract());
	}

	@Test
	@DisplayName("La carga de una imagen (uploadMenuImage) cumple el contrato")
	void imageUploadConformsToTheContract() throws Exception {
		mockMvc.perform(multipart(ApiVersioning.BASE + "/menu/media/images")
				.file(new MockMultipartFile("file", "image.png", MediaType.IMAGE_PNG_VALUE, png()))
				.header("Idempotency-Key", UUID.randomUUID()))
				.andExpect(status().isCreated())
				.andExpect(conformsToContract());
	}

	@Test
	@DisplayName("Una respuesta que el contrato no declara no pasa la validación")
	void rejectsAResponseTheContractDoesNotDeclare() throws Exception {
		// The contract does not declare 422 for listMenuCategories: an invalid page must be caught.
		var result = mockMvc.perform(get(ApiVersioning.BASE + "/menu/categories").param("page", "0")).andReturn();

		var contract = conformsToContract();
		assertThrows(AssertionError.class, () -> contract.match(result));
	}

	private static byte[] png() throws IOException {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", output);
			return output.toByteArray();
		}
	}

}
