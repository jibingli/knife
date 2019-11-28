package knife;

import burp.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CopyHttpDataMenu extends JMenuItem {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    //JMenuItem vs. JMenu
    public CopyHttpDataMenu(BurpExtender burp) {
        this.setText("^_^ copy api template by JBL");
        this.addActionListener(new CopyHttpData_Action(burp, burp.context));
    }
}

class CopyHttpData_Action implements ActionListener {
    private IContextMenuInvocation invocation;
    public IExtensionHelpers helpers;
    public PrintWriter stdout;
    public PrintWriter stderr;
    public IBurpExtenderCallbacks callbacks;
    public BurpExtender burp;


    public CopyHttpData_Action(BurpExtender burp, IContextMenuInvocation invocation) {
        this.burp = burp;
        this.invocation = invocation;
        this.helpers = burp.helpers;
        this.callbacks = burp.callbacks;
        this.stderr = burp.stderr;
        this.stdout = burp.stdout;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        try {
            IHttpRequestResponse[] messages = invocation.getSelectedMessages();
            if (messages != null) {
                String template = "";
                for (IHttpRequestResponse baseRequestResponse : messages) {


                    /////////////selected url/////////////////
                    String path = helpers.analyzeRequest(baseRequestResponse).getUrl().getPath();

                    List<String> headers = helpers.analyzeRequest(baseRequestResponse).getHeaders();
                    String allHeaders = "";
                    for (String string : headers) {
                        if (string.contains(":")) {
                            allHeaders += string + "\r\n";
                        }
                    }
                    IRequestInfo iRequestInfo = helpers.analyzeRequest(baseRequestResponse);
                    String method = iRequestInfo.getMethod();
                    List<IParameter> parameters = iRequestInfo.getParameters();
//                byte contentType = helpers.analyzeRequest(baseRequestResponse).getContentType();
                    template += getSingleRequestTemplate(path, method, parameters);
                }
                setClipboardString(template);
            }
        } catch (Exception e1) {
            e1.printStackTrace(stderr);
        }
    }


    public static void setClipboardString(String text) {
        // 获取系统剪贴板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // 封装文本内容
        Transferable trans = new StringSelection(text);
        // 把文本内容设置到系统剪贴板
        clipboard.setContents(trans, null);
    }

    /**
     * 获取简单Api请求格式模板
     *
     * @param path
     * @param method
     * @param parameters
     * @return
     */
    public String getSingleRequestTemplate(String path, String method, List<IParameter> parameters) throws UnsupportedEncodingException {
        String methodName = getMethodName(path);
        String parametersWithType = "";
        String testMethodStr = "    public HttpResponse " + methodName;
        String apiStr = String.format("requestData.setApi(\"%s\");\n", path);
        String methodStr = String.format("requestData.setMethod(\"%s\");\n", method);
        String queryStr = "requestData.setQueryParams(new HashMap<String, Object>() {{\n";
        String formStr = "requestData.setFormData(new HashMap<String, Object>() {{\n";
        String cookieStr = "requestData.setCookies(new HashMap<String, Object>() {{\n";
        String jsonBodyStr = "requestData.setJsonBody(new JSONObject() {{\n";
        if (parameters != null) {
            for (IParameter iParameter : parameters) {
                byte type = iParameter.getType();
                String name = iParameter.getName();
                String value = new String(iParameter.getValue().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                if (type == IParameter.PARAM_JSON) {
                    jsonBodyStr += "put(\"" + name + "\",\"" + value + "\");\n";
                    parametersWithType += "String " + name + ", ";
                } else if (type == IParameter.PARAM_URL) {
                    queryStr += "put(\"" + name + "\",\"" + value + "\");\n";
                    parametersWithType += "String " + name + ", ";
                } else if (type == IParameter.PARAM_COOKIE) {
                    cookieStr += "put(\"" + name + "\",\"" + value + "\");\n";
                } else if (type == IParameter.PARAM_BODY) {
                    formStr += "put(\"" + name + "\",\"" + value + "\");\n";
                    parametersWithType += "String " + name + ", ";
                }
            }
            if (parametersWithType.length() > 2) {
                parametersWithType = parametersWithType.substring(0, parametersWithType.length() - 2);
            }
            testMethodStr += "(" + parametersWithType + ") {\n" +
                    "        RequestData requestData = new RequestData();\n";
            testMethodStr += apiStr;
            testMethodStr += methodStr;
            queryStr += "}});\n";
            cookieStr += "}});\n";
            formStr += "}});\n";
            jsonBodyStr += "}}.toJSONString());\n";
            if (!queryStr.equals("requestData.setQueryParams(new HashMap<String, Object>() {{\n}});\n")) {
                testMethodStr += queryStr;
            }
            if (!formStr.equals("requestData.setFormData(new HashMap<String, Object>() {{\n}});\n")) {
                testMethodStr += formStr;
            }
            if (!formStr.equals("requestData.setCookies(new HashMap<String, Object>() {{\n}});\n")) {
//                testMethodStr += cookieStr;
            }
            if (!jsonBodyStr.equals("requestData.setJsonBody(new JSONObject() {{\n}}.toJSONString());\n")) {
                testMethodStr += jsonBodyStr;
            }
        }

        testMethodStr += " ApiTemplate template = new ApiTemplate(requestData); \nreturn runner.send(template);\n}\n";

        return testMethodStr;
    }

    public String getMethodName(String path) {
        String methodName;
        String[] paths = path.split("/");
        if (paths.length >= 2) {
            String last = paths[paths.length - 1].toUpperCase();
            methodName = paths[paths.length - 2] + last.substring(0, 1) + last.substring(1).toLowerCase();
        } else {
            methodName = paths[paths.length - 1];
        }
        return methodName;
    }
}
