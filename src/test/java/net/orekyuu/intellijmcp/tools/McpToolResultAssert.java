package net.orekyuu.intellijmcp.tools;

import org.assertj.core.api.AbstractAssert;

class McpToolResultAssert<R> extends AbstractAssert<McpToolResultAssert<R>, McpTool.Result<ErrorResponse, R>> {

    private McpToolResultAssert(McpTool.Result<ErrorResponse, R> actual) {
        super(actual, McpToolResultAssert.class);
    }

    static <R> McpToolResultAssert<R> assertThat(McpTool.Result<ErrorResponse, R> actual) {
        return new McpToolResultAssert<>(actual);
    }

    McpToolResultAssert<R> isError() {
        isNotNull();
        if (!(actual instanceof McpTool.Result.ErrorResponse)) {
            failWithMessage("Expected ErrorResponse but was <%s>", actual.getClass().getSimpleName());
        }
        return this;
    }

    McpToolResultAssert<R> hasErrorMessageContaining(String expected) {
        isError();
        var error = (McpTool.Result.ErrorResponse<ErrorResponse, R>) actual;
        String msg = error.message().message();
        if (!msg.contains(expected)) {
            failWithMessage("Expected error message to contain <%s> but was <%s>", expected, msg);
        }
        return this;
    }

    McpToolResultAssert<R> isSuccess() {
        isNotNull();
        if (!(actual instanceof McpTool.Result.SuccessResponse)) {
            failWithMessage("Expected SuccessResponse but was <%s>", actual.getClass().getSimpleName());
        }
        return this;
    }

    R getSuccessResponse() {
        isSuccess();
        return ((McpTool.Result.SuccessResponse<ErrorResponse, R>) actual).message();
    }
}
