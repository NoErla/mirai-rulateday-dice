package indi.eiriksgata.rulateday.instruction;

import indi.eiriksgata.dice.operation.RollBasics;
import indi.eiriksgata.dice.operation.RollRole;
import indi.eiriksgata.dice.operation.impl.RollRoleImpl;
import indi.eiriksgata.dice.utlis.RegularExpressionUtils;
import indi.eiriksgata.dice.vo.MessageData;
import indi.eiriksgata.dice.config.DiceConfig;
import indi.eiriksgata.dice.exception.DiceInstructException;
import indi.eiriksgata.dice.exception.ExceptionEnum;
import indi.eiriksgata.dice.injection.InstructService;
import indi.eiriksgata.dice.injection.InstructReflex;
import indi.eiriksgata.dice.operation.DiceSet;
import indi.eiriksgata.dice.operation.impl.RollBasicsImpl;
import indi.eiriksgata.dice.reply.CustomText;
import indi.eiriksgata.rulateday.event.EventAdapter;
import indi.eiriksgata.rulateday.event.EventUtils;
import indi.eiriksgata.rulateday.service.HumanNameService;
import indi.eiriksgata.rulateday.service.UserTempDataService;
import indi.eiriksgata.rulateday.service.impl.HumanNameServiceImpl;
import indi.eiriksgata.rulateday.service.impl.UserTempDataServiceImpl;
import indi.eiriksgata.rulateday.utlis.CharacterUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupTempMessageEvent;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Resource;

/**
 * @author: create by Keith
 * @version: v1.0
 * @description: indi.eiriksgata.dice
 * @date:2020/9/24
 **/

@InstructService
public class RollController {

    @Resource
    public static final UserTempDataService userTempDataService = new UserTempDataServiceImpl();

    @Resource
    public static final RollBasics rollBasics = new RollBasicsImpl();

    @Resource
    public static final DiceSet diceSet = new DiceSet();

    @Resource
    public static final RollRole rollRole = new RollRoleImpl();

    @Resource
    public static final HumanNameService humanNameService = new HumanNameServiceImpl();

    @InstructReflex(value = {".ra", ".rc", "。ra", "。rc"}, priority = 2)
    public String attributeCheck(MessageData data) {
        String attribute = userTempDataService.getUserAttribute(data.getQqID());
        data.setMessage(data.getMessage().replaceAll(" ", ""));
        data.setMessage(CharacterUtils.operationSymbolProcessing(
                data.getMessage()
        ));
        if (attribute == null) {
            attribute = "";
        }
        try {
            return rollBasics.attributeCheck(data.getMessage(), attribute);
        } catch (DiceInstructException e) {
            e.printStackTrace();
            return CustomText.getText("dice.attribute.error");
        }
    }

    @InstructReflex(value = {".st", "。st"})
    public String setAttribute(MessageData data) {
        if (data.getMessage().equals("")) {
            return CustomText.getText("dice.set.attribute.error");
        }
        try {
            userTempDataService.updateUserAttribute(data.getQqID(), data.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return CustomText.getText("dice.set.attribute.error");
        }
        return CustomText.getText("dice.set.attribute.success");
    }

    @InstructReflex(value = {".r", "。r"})
    public String roll(MessageData data) {
        Integer diceFace = userTempDataService.getUserDiceFace(data.getQqID());
        data.setMessage(CharacterUtils.operationSymbolProcessing(data.getMessage()));
        if (diceFace != null) {
            diceSet.setDiceFace(data.getQqID(), diceFace);
        }
        if (data.getMessage().equals("") || data.getMessage().equals(" ")) {
            return rollBasics.rollRandom("d", data.getQqID());
        } else {
            //正则筛选
            String result = RegularExpressionUtils.getMatcher("[0-9dD +\\-*/＋－×÷]+", data.getMessage());
            if (result != null) {
                return rollBasics.rollRandom(result, data.getQqID()) + data.getMessage().replace(result, "");
            }
            return rollBasics.rollRandom(data.getMessage(), data.getQqID());
        }
    }


    @InstructReflex(value = {".MessageData", ".set", "。set"})
    public String setDiceFace(MessageData data) throws DiceInstructException {
        //移除所有的空格
        int setDiceFace = 0;
        data.setMessage(data.getMessage().replaceAll(" ", ""));
        try {
            setDiceFace = Integer.valueOf(data.getMessage());
        } catch (Exception e) {
            return CustomText.getText("dice.set.face.error");
        }

        if (setDiceFace > Integer.valueOf(DiceConfig.diceSet.getString("dice.face.max"))) {
            throw new DiceInstructException(ExceptionEnum.DICE_SET_FACE_MAX_ERR);
        }
        if (setDiceFace <= Integer.valueOf(DiceConfig.diceSet.getString("dice.face.min"))) {
            throw new DiceInstructException(ExceptionEnum.DICE_SET_FACE_MIN_ERR);
        }
        diceSet.setDiceFace(data.getQqID(), setDiceFace);
        userTempDataService.updateUserDiceFace(data.getQqID(), setDiceFace);
        return CustomText.getText("dice.set.face.success", setDiceFace);
    }

    @InstructReflex(value = {".sc", "。sc"})
    public String sanCheck(MessageData data) {
        data.setMessage(CharacterUtils.operationSymbolProcessing(data.getMessage()));

        //检查指令前缀空格符
        for (int i = 0; i < data.getMessage().length(); i++) {
            if (data.getMessage().charAt(i) != ' ') {
                data.setMessage(data.getMessage().substring(i));
                break;
            }
        }

        //优先检测指令是否包含有数值
        if (data.getMessage().matches("(([0-9]?[Dd][0-9]+|[Dd]|[0-9])\\+?)+/(([0-9]?[Dd][0-9]+|[Dd]|[0-9])\\+?)+ [0-9]+")) {
            //检测到包含数值 进行 空格符 分割 0为计算公式，1为给定的数值
            String[] tempArr = data.getMessage().split(" ");
            return rollBasics.sanCheck(tempArr[0], "san" + tempArr[1], (attribute, random, sanValue, calculationProcess, surplus) -> {
            });
        }

        //检测用户输入的指令格式是否正确
        if (data.getMessage().matches("(([0-9]?[Dd][0-9]+|[Dd]|[0-9])\\+?)+/(([0-9]?[Dd][0-9]+|[Dd]|[0-9])\\+?)+")) {
            //查询用户数据
            String attribute = userTempDataService.getUserAttribute(data.getQqID());
            String inputData = RegularExpressionUtils.getMatcher("(([0-9]?[Dd][0-9]+|[Dd]|[0-9])\\+?)+/(([0-9]?[Dd][0-9]+|[Dd]|[0-9])\\+?)+", data.getMessage());
            //要进行是否有用户属性确认
            //对于没有属性的用户 返回错误
            if (attribute == null) {
                return CustomText.getText("dice.sc.not-found.error");
            }

            return rollBasics.sanCheck(inputData, attribute, (resultAttribute, random, sanValue, calculationProcess, surplus) -> {
                //修改属性
                userTempDataService.updateUserAttribute(data.getQqID(), resultAttribute);
            });

        }
        return CustomText.getText("dice.sc.instruct.error");
    }

    @InstructReflex(value = {".rh", "。rh"}, priority = 3)
    public String rollHide(MessageData data) {
        EventUtils.eventCallback(data.getEvent(), new EventAdapter() {
            @Override
            public void group(GroupMessageEvent event) {
                event.getSender().sendMessage(roll(data));
            }

            @Override
            public void friend(FriendMessageEvent event) {
                event.getFriend().sendMessage(roll(data));
            }

            @Override
            public void groupTemp(GroupTempMessageEvent event) {
                event.getSender().sendMessage(roll(data));
            }
        });
        return CustomText.getText("coc7.roll.hide");
    }

    @InstructReflex(value = {".rb", "。rb", ",rb"}, priority = 3)
    public String rollBonusDice(MessageData data) {
        data.setMessage(data.getMessage().replaceAll(" ", ""));
        data.setMessage(CharacterUtils.operationSymbolProcessing(data.getMessage()));

        String attribute = userTempDataService.getUserAttribute(data.getQqID());
        return rollBasics.rollBonus(data.getMessage(), attribute, true);
    }

    @InstructReflex(value = {".rp", "。rp", ",rp", ".Rp"}, priority = 3)
    public String rollPunishment(MessageData data) {
        data.setMessage(data.getMessage().replaceAll(" ", ""));
        data.setMessage(CharacterUtils.operationSymbolProcessing(data.getMessage()));
        String attribute = userTempDataService.getUserAttribute(data.getQqID());
        return rollBasics.rollBonus(data.getMessage(), attribute, false);
    }

    @InstructReflex(value = {".coc", "。coc", ".Coc"})
    public String randomCocRole(MessageData data) {
        int createNumber;
        createNumber = checkCreateRandomRoleNumber(data.getMessage());
        if (createNumber == -1) return CustomText.getText("dice.base.parameter.error");
        if (createNumber > 20 | createNumber < 1) {
            return "参数范围需要在1-20内";
        }
        return rollRole.createCocRole(createNumber);
    }

    @InstructReflex(value = {".dnd", "。dnd", ".Dnd", "。DND"})
    public String randomDndRole(MessageData data) {
        int createNumber;
        createNumber = checkCreateRandomRoleNumber(data.getMessage());
        if (createNumber == -1) return CustomText.getText("dice.base.parameter.error");
        if (createNumber > 20 | createNumber < 1) {
            return "参数范围需要在1-20内";
        }
        return rollRole.createDndRole(createNumber);
    }

    @InstructReflex(value = {".jrrp", ".JRRP", "。jrrp", ".todayRandom"})
    public String todayRandom(MessageData data) {
        return rollBasics.todayRandom(data.getQqID(), 8);
    }


    @InstructReflex(value = {".name"})
    public String randomName(MessageData data) {
        if (StringUtils.isNumeric(data.getMessage())) {
            int number = 0;
            try {
                number = Integer.valueOf(data.getMessage());
            } catch (Exception e) {
                return CustomText.getText("dice.base.parameter.error");
            }
            if (number > 0 && number <= 20) {
                return humanNameService.randomName(Integer.valueOf(data.getMessage()));
            }
            return "参数范围在1-20内";
        } else {
            return humanNameService.randomName(1);
        }
    }

    private int checkCreateRandomRoleNumber(String message) {
        if (message.equals("")) {
            return 1;
        } else {
            try {
                return Integer.valueOf(message);
            } catch (Exception e) {
                return -1;
            }
        }
    }


}
