package ren.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ren.util.DelayMessage;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 文件操作方式实现持久化
 */
public class FilePersistImpl implements Persist {

    private static Logger LOGGER = LoggerFactory.getLogger(FilePersistImpl.class);

    private String FileLocalPath = "/user/local/delayCache/";
    private String fileName = "queueMessageDB";

    private File messageStore ;

    private Thread messageThread;


    @Override
    public void runPersist(long minTime) {

    }

    @Override
    public List<DelayMessage> loadFromPersist() {
        return null;
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


    public static void main(String[] args) throws IOException {
        File file = new File("/usr/local/delayCache/");
        file.mkdirs();
        System.out.println("------");
    }
}
