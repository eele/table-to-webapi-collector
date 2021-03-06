package io.eele.collector.components;

import io.eele.collector.ApiCollector;
import io.eele.collector.entities.CallerMethod;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class XmlReader {

//    public static void main(String[] args) {
//        System.out.println(JSON.toJSONString(getMappers(Arrays.asList("TD_VIP_ROLE")), SerializerFeature.PrettyFormat));
//    }

    public static List<CallerMethod> getMappers(List<String> tableNameList) {

        List<String> list = FolderFileScanner
                .scanFilesWithNoRecursion(ApiCollector.BASE_DIR_PATH)
                .stream().filter(o -> o.endsWith(".xml")).distinct().collect(Collectors.toList());
        List<CallerMethod> callerMethodList = new ArrayList<>();
        list.forEach(f -> {
            try {
                StringBuilder buffer = new StringBuilder();
                InputStream is = new FileInputStream(f);
                String line; // 用来保存每行读取的内容
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                line = reader.readLine(); // 读取第一行
                while (line != null) { // 如果 line 为空说明读完了
                    buffer.append(line); // 将读到的内容添加到 buffer 中
                    buffer.append("\u2502\u2502"); // 添加换行符
                    line = reader.readLine(); // 读取下一行
                }
                reader.close();
                is.close();

                String xmlContent = buffer.toString();
                String tableJoin = String.join("|", tableNameList);
                Pattern pattern = Pattern.compile(".*(" + tableJoin + ").*", Pattern.CASE_INSENSITIVE);

                String mapperClassName = getSubUtil(xmlContent, "<( )*mapper( )*namespace.*?(?i)(" + tableJoin + ")")
                        .stream().findFirst().orElse("").replaceAll("<( )*mapper( )*namespace( )*=( )*\"(.*?)\".*$", "$5");
                if (StringUtils.isBlank(mapperClassName)) return;

                System.out.println(mapperClassName);

                List<String> sqlList = new ArrayList<>();

                int i = 0;
                while (true) {
                    i = xmlContent.indexOf("<", i + 1);
                    if (i == -1) {
                        break;
                    }
                    String action = xmlContent.substring(i, i + 7);
                    switch (action) {
                        case "<insert": {
                            String sql = xmlContent.substring(i, xmlContent.indexOf("insert>", i + 1) + 7);
                            if (pattern.matcher(sql).matches()) {
                                sqlList.add(sql);
                            }
                            i += sql.length();
                            break;
                        }
                        case "<delete": {
                            String sql = xmlContent.substring(i, xmlContent.indexOf("delete>", i + 1) + 7);
                            if (pattern.matcher(sql).matches()) {
                                sqlList.add(sql);
                            }
                            i += sql.length();
                            break;
                        }
                        case "<update": {
                            String sql = xmlContent.substring(i, xmlContent.indexOf("update>", i + 1) + 7);
                            if (pattern.matcher(sql).matches()) {
                                sqlList.add(sql);
                            }
                            i += sql.length();
                            break;
                        }
                        case "<select": {
                            String sql = xmlContent.substring(i, xmlContent.indexOf("select>", i + 1) + 7);
                            if (pattern.matcher(sql).matches()) {
                                sqlList.add(sql);
                            }
                            i += sql.length();
                            break;
                        }
                    }
                }

                callerMethodList.addAll(sqlList.stream().map(s -> {
                    CallerMethod callerMethod = new CallerMethod();
                    callerMethod.setClassName(mapperClassName);
                    callerMethod.setMethodName(s.replaceAll("<( )*(insert|delete|update|select)( )*id( )*=( )*\"(.*?)\".*$", "$6"));
                    callerMethod.setTableNames(getSubUtil(s, "(?i)(" + tableJoin + ")").stream().distinct().collect(Collectors.toList()));
                    callerMethod.setSql(s.replaceAll("\u2502\u2502", "\n"));
                    return callerMethod;
                }).collect(Collectors.toList()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return callerMethodList.stream().distinct().collect(Collectors.toList());
    }



    /**
     * 正则表达式匹配两个指定字符串中间的内容
     * @param soap
     * @return
     */
    public static List<String> getSubUtil(String soap,String rgex){
        List<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile(rgex/*, Pattern.CASE_INSENSITIVE*/);// 匹配的模式
        Matcher m = pattern.matcher(soap);
        while (m.find()) {
            list.add(m.group(0));
        }
        return list;
    }

}
