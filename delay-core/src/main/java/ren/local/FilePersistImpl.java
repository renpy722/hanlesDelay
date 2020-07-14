package ren.local;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ren.util.DelayMessage;
import ren.util.GlobalConfig;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 文件操作方式实现持久化
 */
public class FilePersistImpl implements Persist {

    private static Logger LOGGER = LoggerFactory.getLogger(FilePersistImpl.class);

    private String FileLocalPath = "/user/local/delayCache/";
    private String fileName = "queueMessageDB";

    private File messageStore ;

    private Lock fileLock = new ReentrantLock();


    private List<DelayMessage> localDelayMsg = new ArrayList<>();

    public List<DelayMessage> getLocalDelayMsg() {
        return localDelayMsg;
    }

    @Override
    public void runPersist(List<DelayMessage> list) {

        localDelayMsg.addAll(list);
        GlobalConfig.GlobalThreadPool.submit(()->{
            try {
                if (!fileLock.tryLock(2, TimeUnit.SECONDS)){
                    LOGGER.warn("正在执行数据落库，本次退出");
                    return;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                TreeMap<Long, List<DelayMessage>> oldMsg = readFile();
                if (getLocalDelayMsg().size()==0){
                    LOGGER.debug("本次缓存无更新，尝试删除旧的执行过的数据");
                }else {
                    getLocalDelayMsg().forEach(item ->{
                        if(oldMsg.containsKey(item.getExecuteTime())){
                            oldMsg.get(item.getExecuteTime()).add(item);
                        }else {
                            List itemList = new ArrayList();
                            itemList.add(item);
                            oldMsg.put(item.getExecuteTime(),itemList);
                        }
                    });
                }
                Long firstKey = oldMsg.firstKey();
                Long nowTime = System.currentTimeMillis();
                while (firstKey.longValue() <= nowTime.longValue()-1000 ){
                    oldMsg.remove(firstKey);
                    if (oldMsg.size()==0){
                        break;
                    }
                    firstKey = oldMsg.firstKey();
                }
                writeFile(oldMsg);
                LOGGER.debug("once persist execute finush");
                localDelayMsg.clear();
            } catch (IOException e ) {
                LOGGER.error("执行消息文件存储失败：{}",e);
                e.printStackTrace();
                localDelayMsg.clear();
            }finally {
                fileLock.unlock();
            }

        });
    }

    private class FileThread implements Runnable{

        private List<DelayMessage> delayMessages = null;
        @Override
        public void run() {

        }

        public void setDelayMessages(List<DelayMessage> paraList){
            delayMessages = paraList;
        }
    }

    @Override
    public List<DelayMessage> loadFromPersist() {
        try {
            TreeMap<Long, List<DelayMessage>> result = readFile();
            List<DelayMessage> listResult = new ArrayList<DelayMessage>();
            result.values().forEach(item -> listResult.addAll(item));
            return listResult;
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("读取文件系统持久化数据失败");
        }
        return new ArrayList<>();
    }

    private TreeMap<Long, List<DelayMessage>> readFile() throws IOException{
        TreeMap<Long, List<DelayMessage>> result = null;
        File msgFile = new File(FileLocalPath+File.separator+fileName);
        FileReader fileReader = new FileReader(msgFile);
        BufferedReader reader = new BufferedReader(fileReader);
        try{
            //操作文件，返回格式数据
            StringBuilder resultStr = new StringBuilder();
            String lineStr = reader.readLine();
            while (lineStr!=null){
                resultStr.append(lineStr);
                lineStr = reader.readLine();
            }
            if (resultStr==null||resultStr.length()==0){
                return new TreeMap<>();
            }
            result = JSONObject.parseObject(resultStr.toString(),new TypeReference<TreeMap<Long,List<DelayMessage>>>(){});
            return result;
        }catch (Exception e){
            LOGGER.error("执行文件操作异常：{}",e);
        }finally {
            reader.close();
            fileReader.close();
        }
        return new TreeMap<>();
    }

    private void writeFile(TreeMap<Long, List<DelayMessage>> map) throws IOException {
        TreeMap<Long, List<DelayMessage>> result = null;
        File msgFile = new File(FileLocalPath+File.separator+fileName);
        if (msgFile.exists()){
            msgFile.delete();
        }
        msgFile.createNewFile();
        FileWriter fileWriter = new FileWriter(msgFile);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        try{
            bufferedWriter.write(JSON.toJSONString(map));
        }catch (Exception e){
            LOGGER.error("执行文件操作异常：{}",e);
        }finally {
            bufferedWriter.close();
            fileWriter.close();
        }


    }
    public void init(){
        try {
            initCheck();

            /**todo 创建一个死循环线程，来处理持久化，这里的持久化参数考虑接入用户自定义，所以还需要改造读取用户配置信息的
             * 逻辑，创建一个全局的配置中心，将所有的数据存储在其中，各个组件想用的时候随时能用
             */
            //todo 检查存储文件是否存在，不存在则创建

            //todo 需要考虑消息的识别问题，可以考虑时间key与List的方式存储为properties文件
        }catch (Exception e){
            LOGGER.error("开启消息持久化失败");
        }
    }

    private void initCheck() throws IOException {
        File dbDir = new File(FileLocalPath);
        if (!dbDir.exists()){
            LOGGER.warn("delay cache local store dir not exist，will to create new one");
            dbDir.mkdirs();
        }
        File dbFile = new File(FileLocalPath+File.separator+fileName);
        if (!dbFile.exists()){
            LOGGER.warn("delay cache local store file not exist，will to create new one");
            dbFile.createNewFile();
        }
        messageStore = dbFile;
    }

    private static class FilePersistImplHolder{
        private static FilePersistImpl instance = new FilePersistImpl();
    }

    private FilePersistImpl() {
    }

    public static FilePersistImpl getInstance(){
        return FilePersistImplHolder.instance;
    }

}
