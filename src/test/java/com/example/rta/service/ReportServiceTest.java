package com.example.rta.service;

import com.example.rta.dto.NormalizedSentenceDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReportServiceTest {

	private static String invokePrivateRemove(ReportService svc, List<NormalizedSentenceDto> matches) throws Exception {
		Method m = ReportService.class.getDeclaredMethod("removeDuplicateMatchesAndGetIds", List.class);
		m.setAccessible(true);
		return (String) m.invoke(svc, matches);
	}

	@Test
	public void testEmptyList() throws Exception {
		ReportService svc = new ReportService(null, null);
		List<NormalizedSentenceDto> matches = new ArrayList<>();
		String res = invokePrivateRemove(svc, matches);
		assertEquals("", res);
	}

	@Test
	public void testSingleWithId() throws Exception {
		ReportService svc = new ReportService(null, null);
		List<NormalizedSentenceDto> matches = List.of(new NormalizedSentenceDto(42, "hello"));
		String res = invokePrivateRemove(svc, matches);
		assertEquals("42", res);
	}

	@Test
	public void testContainedMatchesFiltered() throws Exception {
		ReportService svc = new ReportService(null, null);
		// two matches, one contained in the other: keep only the longer (id=2)
		NormalizedSentenceDto shortOne = new NormalizedSentenceDto(1, "pedale de frein");
		NormalizedSentenceDto longOne = new NormalizedSentenceDto(2, "appuyer sur la pedale de frein");
		List<NormalizedSentenceDto> matches = List.of(shortOne, longOne);
		String res = invokePrivateRemove(svc, matches);
		assertEquals("2", res);
	}

	@Test
	public void testMultipleNonOverlappingKeptInOrder() throws Exception {
		ReportService svc = new ReportService(null, null);
		NormalizedSentenceDto a = new NormalizedSentenceDto(1, "bbbb");
		NormalizedSentenceDto b = new NormalizedSentenceDto(2, "aaaaa");
		NormalizedSentenceDto c = new NormalizedSentenceDto(3, "ccx");
		NormalizedSentenceDto d = new NormalizedSentenceDto(4, "bbb");
		NormalizedSentenceDto e = new NormalizedSentenceDto(5, "ccx");
		List<NormalizedSentenceDto> matches = List.of(a, b, c, d, e);

		String res = invokePrivateRemove(svc, matches);
		// sorted by length desc -> aaaaa (5), bbbb (4), ccx (3) => ids "2,1,3"; 2 results were filtered
		assertEquals("2,1,3", res);
	}
}

