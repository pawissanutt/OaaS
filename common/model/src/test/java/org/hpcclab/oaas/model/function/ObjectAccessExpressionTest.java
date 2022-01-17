package org.hpcclab.oaas.model.function;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ObjectAccessExpressionTest {

  String id() {
    return UUID.randomUUID().toString();
  }

  @Test
  void testValid() {
    assertTrue(ObjectAccessExpression.validate(id()));
    assertTrue(ObjectAccessExpression.validate("%s:test()".formatted(id())));
    assertTrue(ObjectAccessExpression.validate("%s:test(%s)".formatted(id(),id())));
    assertTrue(ObjectAccessExpression.validate("%s:test(%s,%s)"
      .formatted(id(),id(), id())));
    assertTrue(ObjectAccessExpression.validate("%s:test(%s,%s,%s)"
      .formatted(id(),id(), id(), id())));
    assertTrue(ObjectAccessExpression.validate(
      "%s:test()()".formatted(id())));
    assertTrue(ObjectAccessExpression.validate(
      "%s:test()(test=aaa)".formatted(id())));
    assertTrue(ObjectAccessExpression.validate(
      "%s:test()(aaa=111;bbb=222)".formatted(id())));
    assertTrue(ObjectAccessExpression.validate(
      "%s:test(%s)(aaa=111;bbb=222)".formatted(id(),id())));
    assertTrue(ObjectAccessExpression.validate(
      "%s:test(%s,%s)(aaa=111;bbb=222)".formatted(id(),id(),id())));
  }
  @Test
  void testInvalid() {
    assertFalse(ObjectAccessExpression.validate(id()+':'));
    assertFalse(ObjectAccessExpression.validate("%s:test".formatted(id())));
    assertFalse(ObjectAccessExpression.validate("%s:test(TE__)".formatted(id())));
    assertFalse(ObjectAccessExpression.validate("%s:test()())".formatted(id())));
  }

  @Test
  void testParse() {
    var ids = List.of(id(),id(),id(),id());
    var fc = ObjectAccessExpression.parse(
      ids.get(0)
    );
    assertNotNull(fc);
    assertEquals(ids.get(0),fc.getTarget().toString());
    assertNull(fc.getFunctionName());

    fc = ObjectAccessExpression.parse(
      "%s:test()".formatted(ids.get(0))
    );
    assertNotNull(fc);
    assertEquals(ids.get(0),fc.getTarget().toString());
    assertEquals("test",fc.functionName.toString());
    assertNull(fc.getInputs());
    assertNull(fc.getArgs());


    fc = ObjectAccessExpression.parse(
      "%s:test(%s)".formatted(ids.get(0), ids.get(1))
    );
    assertNotNull(fc);
    assertEquals(ids.get(0),fc.getTarget().toString());
    assertEquals("test",fc.functionName.toString());
    assertNotNull(fc.getInputs());
    assertEquals(1, fc.getInputs().size());
    assertEquals(ids.get(1), fc.getInputs().get(0).toString());
    assertNull(fc.getArgs());

    fc = ObjectAccessExpression.parse(
      "%s:test(%s,%s)()".formatted(ids.get(0), ids.get(1), ids.get(2))
    );
    assertNotNull(fc);
    assertEquals(ids.get(0),fc.getTarget().toString());
    assertEquals("test",fc.functionName.toString());
    assertNotNull(fc.getInputs());
    assertEquals(2, fc.getInputs().size());
    assertEquals(ids.get(1), fc.getInputs().get(0).toString());
    assertEquals(ids.get(2), fc.getInputs().get(1).toString());
    assertNull(fc.getArgs());


    fc = ObjectAccessExpression.parse(
      "%s:test(%s,%s,%s)(aaa=bbb)".formatted(ids.get(0), ids.get(1), ids.get(2),
        ids.get(3))
    );
    assertNotNull(fc);
    assertEquals(ids.get(0),fc.getTarget().toString());
    assertEquals("test",fc.functionName.toString());
    assertNotNull(fc.getInputs());
    assertEquals(3, fc.getInputs().size());
    assertEquals(ids.get(1), fc.getInputs().get(0).toString());
    assertEquals(ids.get(2), fc.getInputs().get(1).toString());
    assertEquals(ids.get(3), fc.getInputs().get(2).toString());
    assertNotNull(fc.getArgs());
    assertEquals(1, fc.getArgs().size());
    assertEquals("bbb", fc.getArgs().get("aaa"));
    assertNull(fc.getArgs().get("ccc"));

    fc = ObjectAccessExpression.parse(
      "%s:test(%s,%s,%s)(aaa=111;122-/*=*/-++})".formatted(ids.get(0), ids.get(1), ids.get(2),
        ids.get(3))
    );
    assertNotNull(fc);
    assertEquals(ids.get(0),fc.getTarget().toString());
    assertEquals("test",fc.functionName.toString());
    assertNotNull(fc.getInputs());
    assertEquals(3, fc.getInputs().size());
    assertEquals(ids.get(1), fc.getInputs().get(0).toString());
    assertEquals(ids.get(2), fc.getInputs().get(1).toString());
    assertEquals(ids.get(3), fc.getInputs().get(2).toString());
    assertNotNull(fc.getArgs());
    assertEquals(2, fc.getArgs().size());
    assertEquals("111", fc.getArgs().get("aaa"));
    assertEquals("*/-++}", fc.getArgs().get("122-/*"));
  }

  @Test
  void testToString() {
    var ids = IntStream.range(0,3)
      .mapToObj(i -> UUID.randomUUID())
      .toList();
    var fc = new ObjectAccessExpression()
      .setTarget(ids.get(0));
    assertEquals(
      ids.get(0).toString(),
      fc.toString()
    );

    fc = new ObjectAccessExpression()
      .setTarget(ids.get(0))
      .setFunctionName("test");
    assertEquals(
      ids.get(0).toString() + ":test()",
      fc.toString()
    );

    fc = new ObjectAccessExpression()
      .setTarget(ids.get(0))
      .setFunctionName("test")
      .setInputs(List.of(ids.get(1)));
    assertEquals(
      "%s:test(%s)".formatted(ids.get(0),ids.get(1)),
      fc.toString()
    );

    fc = new ObjectAccessExpression()
      .setTarget(ids.get(0))
      .setFunctionName("more.test")
      .setInputs(List.of(ids.get(1),ids.get(2)));
    assertEquals(
      "%s:more.test(%s,%s)".formatted(ids.get(0),ids.get(1), ids.get(2)),
      fc.toString()
    );

    fc = new ObjectAccessExpression()
      .setTarget(ids.get(0))
      .setFunctionName("more.test")
      .setInputs(List.of(ids.get(1),ids.get(2)))
      .setArgs(Map.of("aaa","bbb"));
    assertEquals(
      "%s:more.test(%s,%s)(aaa=bbb)".formatted(ids.get(0),ids.get(1), ids.get(2)),
      fc.toString()
    );

    fc = new ObjectAccessExpression()
      .setTarget(ids.get(0))
      .setFunctionName("more.test")
      .setInputs(List.of(ids.get(1),ids.get(2)))
      .setArgs(Map.of("aaa","bbb", "231aa^()", "-*/++"));
    assertEquals(
      "%s:more.test(%s,%s)(231aa^()=-*/++;aaa=bbb)".formatted(ids.get(0),ids.get(1), ids.get(2)),
      fc.toString()
    );
  }
}
