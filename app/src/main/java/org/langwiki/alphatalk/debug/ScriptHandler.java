package org.langwiki.alphatalk.debug;

import org.langwiki.alphatalk.debug.cli.LineProcessor;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.StringWriter;

/*package*/ class ScriptHandler implements LineProcessor {
    protected String prompt;
    protected ScriptEngine mScriptEngine;
    protected ScriptContext mScriptContext;
    protected StringWriter mWriter;

    public ScriptHandler(String prompt, ScriptEngine engine) {
        this.prompt = prompt;
        mScriptEngine = engine;
        mScriptContext = mScriptEngine.getContext();

        mWriter = new StringWriter();
        mScriptContext.setWriter(mWriter);
        mScriptContext.setErrorWriter(mWriter);
    }

    @Override
    public String processLine(String line) {
        try {
            mWriter.getBuffer().setLength(0);
            mScriptEngine.eval(line, mScriptContext);
            mWriter.flush();
            if (mWriter.getBuffer().length() != 0)
                return mWriter.toString();
            else
                return "";
        } catch (ScriptException e) {
            return e.toString();
        }
    }

    @Override
    public String getPrompt() {
        return prompt;
    }
}
