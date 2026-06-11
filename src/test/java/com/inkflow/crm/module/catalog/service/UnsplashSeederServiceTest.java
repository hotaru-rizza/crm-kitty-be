package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class UnsplashSeederServiceTest {

    private static final String BASE_URL = "http://localhost/unsplash";

    @Mock
    private TattooRepository tattooRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private TattooTaggerService taggerService;

    @InjectMocks
    private UnsplashSeederService seederService;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        ReflectionTestUtils.setField(seederService, "restClient", builder.build());
        ReflectionTestUtils.setField(seederService, "apiKey", "unsplash-test-key");
        ReflectionTestUtils.setField(seederService, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(seederService, "query", "tattoo");
        ReflectionTestUtils.setField(seederService, "pages", 1);
        ReflectionTestUtils.setField(seederService, "perPage", 10);
    }

    @Test
    void shouldSaveNewPhotosAndSkipDuplicates() {
        mockUnsplashPage("""
                {
                  "results": [
                    {
                      "id": "photo-new",
                      "width": 1200,
                      "height": 800,
                      "blur_hash": "abc123",
                      "color": "#112233",
                      "description": "Rose tattoo",
                      "alt_description": "traditional rose",
                      "urls": {
                        "regular": "https://images.unsplash.com/regular.jpg",
                        "small": "https://images.unsplash.com/small.jpg"
                      },
                      "user": {
                        "name": "Jane Doe",
                        "links": { "html": "https://unsplash.com/@jane" }
                      }
                    },
                    {
                      "id": "photo-existing",
                      "width": 900,
                      "height": 600,
                      "blur_hash": "xyz",
                      "color": "#ffffff",
                      "description": "Existing",
                      "alt_description": "existing tattoo",
                      "urls": {
                        "regular": "https://images.unsplash.com/existing-regular.jpg",
                        "small": "https://images.unsplash.com/existing-small.jpg"
                      },
                      "user": {
                        "name": "John Smith",
                        "links": { "html": "https://unsplash.com/@john" }
                      }
                    }
                  ],
                  "total": 2
                }
                """);

        when(tattooRepository.existsBySourceAndSourceId(Tattoo.SOURCE_UNSPLASH, "photo-new")).thenReturn(false);
        when(tattooRepository.existsBySourceAndSourceId(Tattoo.SOURCE_UNSPLASH, "photo-existing")).thenReturn(true);
        when(taggerService.tagFromText("traditional rose", "Rose tattoo")).thenReturn(new String[]{"traditional"});
        when(embeddingService.embedPassage(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        int saved = seederService.seed();

        assertEquals(1, saved);

        ArgumentCaptor<Tattoo> tattooCaptor = ArgumentCaptor.forClass(Tattoo.class);
        verify(tattooRepository, times(1)).save(tattooCaptor.capture());

        Tattoo savedTattoo = tattooCaptor.getValue();
        assertEquals(Tattoo.SOURCE_UNSPLASH, savedTattoo.getSource());
        assertEquals("photo-new", savedTattoo.getSourceId());
        assertEquals("https://images.unsplash.com/regular.jpg", savedTattoo.getImageUrl());
        assertEquals("https://images.unsplash.com/small.jpg", savedTattoo.getThumbnailUrl());
        assertEquals(1200, savedTattoo.getWidth());
        assertEquals(800, savedTattoo.getHeight());
        assertEquals("abc123", savedTattoo.getBlurHash());
        assertEquals("#112233", savedTattoo.getDominantColor());
        assertEquals("Jane Doe", savedTattoo.getAuthorName());
        assertTrue(savedTattoo.getAuthorUrl().contains("utm_source=inkflow"));
        assertEquals("Rose tattoo", savedTattoo.getDescription());
        assertEquals("traditional rose", savedTattoo.getAltDescription());
        assertArrayEquals(new String[]{"traditional"}, savedTattoo.getTags());
        assertArrayEquals(new float[]{0.1f, 0.2f}, savedTattoo.getEmbedding());

        verify(embeddingService).embedPassage(savedTattoo.buildEmbedText());
        mockServer.verify();
    }

    @Test
    void shouldStopWhenSearchReturnsNoResults() {
        mockUnsplashPage("""
                {
                  "results": null,
                  "total": 0
                }
                """);

        int saved = seederService.seed();

        assertEquals(0, saved);
        verify(tattooRepository, never()).save(any());
        mockServer.verify();
    }

    @Test
    void shouldReturnZeroWhenPageFetchFails() {
        mockServer.expect(requestTo(BASE_URL + "/search/photos?query=tattoo&page=1&per_page=10&order_by=relevant"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Client-ID unsplash-test-key"))
                .andRespond(withServerError());

        int saved = seederService.seed();

        assertEquals(0, saved);
        verify(tattooRepository, never()).save(any());
        mockServer.verify();
    }

    private void mockUnsplashPage(String jsonBody) {
        mockServer.expect(requestTo(BASE_URL + "/search/photos?query=tattoo&page=1&per_page=10&order_by=relevant"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Client-ID unsplash-test-key"))
                .andExpect(queryParam("query", "tattoo"))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("per_page", "10"))
                .andRespond(withSuccess(jsonBody, MediaType.APPLICATION_JSON));
    }
}
