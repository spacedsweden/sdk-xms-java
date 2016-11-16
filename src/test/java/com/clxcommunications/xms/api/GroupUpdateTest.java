package com.clxcommunications.xms.api;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.clxcommunications.xms.ApiObjectMapper;
import com.clxcommunications.xms.TestUtils;
import com.clxcommunications.xms.Utils;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;

@RunWith(JUnitQuickcheck.class)
public class GroupUpdateTest {

	private final ApiObjectMapper json = new ApiObjectMapper();

	@Test
	public void canSerializeMinimal() throws Exception {
		GroupUpdate input = GroupUpdate.builder().build();

		String expected = "{}";
		String actual = json.writeValueAsString(input);

		assertThat(actual, is(TestUtils.jsonEqualTo(expected)));
	}

	@Test
	public void canSerializeMaximalish() throws Exception {
		GroupUpdate input = GroupUpdate.builder()
		        .unsetName()
		        .addMemberAdd("123456789")
		        .addMemberRemove("987654321", "4242424242")
		        .childGroupsAdd(Arrays.asList("group1", "group2"))
		        .addChildGroupsRemove("group3")
		        .autoUpdate(ImmutableAutoUpdate.builder()
		                .to("1111")
		                .addKeywordFirst("kw0")
		                .addKeywordSecond("kw1")
		                .removeKeywordFirst("kw2")
		                .removeKeywordSecond("kw3")
		                .build())
		        .build();

		String expected = Utils.join("\n",
		        "{",
		        "  'name': null,",
		        "  'add': [ '123456789' ],",
		        "  'remove': [ '987654321', '4242424242' ],",
		        "  'child_groups_add': [ 'group1', 'group2' ],",
		        "  'child_groups_remove': [ 'group3' ],",
		        "  'auto_update': {",
		        "    'to': '1111',",
		        "    'add_keyword_first': 'kw0',",
		        "    'add_keyword_second': 'kw1',",
		        "    'remove_keyword_first': 'kw2',",
		        "    'remove_keyword_second': 'kw3'",
		        "  }",
		        "}").replace('\'', '"');

		String actual = json.writeValueAsString(input);

		assertThat(actual, is(TestUtils.jsonEqualTo(expected)));
	}

}
