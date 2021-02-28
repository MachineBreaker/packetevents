package io.github.retrooper.packetevents.utils.attributesnapshot;

import io.github.retrooper.packetevents.packettype.PacketTypeClasses;
import io.github.retrooper.packetevents.packetwrappers.NMSPacket;
import io.github.retrooper.packetevents.packetwrappers.WrappedPacket;
import io.github.retrooper.packetevents.utils.nms.NMSUtils;
import io.github.retrooper.packetevents.utils.reflection.Reflection;
import io.github.retrooper.packetevents.utils.reflection.SubclassUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class AttributeSnapshotWrapper extends WrappedPacket {
    private static byte constructorMode;
    private static Constructor<?> attributeSnapshotConstructor, attributeBaseConstructor;
    private static Class<?> attributeSnapshotClass, attributeBaseClass, iRegistryClass;
    private static Field iRegistryAttributeBaseField;
    private static Method getiRegistryByMinecraftKeyMethod;
    private static boolean stringKeyPresent;

    public AttributeSnapshotWrapper(NMSPacket packet) {
        super(packet);
    }

    public AttributeSnapshotWrapper(String key, double value, List<AttributeModifierWrapper> modifiers) {
        super(Objects.requireNonNull(create(key, value, modifiers)).packet);
    }

    @Override
    protected void load() {
        stringKeyPresent = Reflection.getField(packet.getRawNMSPacket().getClass(), String.class, 0) != null;
        if (attributeBaseClass == null) {
            attributeBaseClass = NMSUtils.getNMSClassWithoutException("AttributeBase");
        }
        if (attributeSnapshotConstructor == null) {
            try {
                attributeSnapshotConstructor = attributeSnapshotClass.getConstructor(attributeBaseClass, double.class, Collection.class);
            } catch (NoSuchMethodException e3) {

            }
        }
        Class<?> iRegistryClass = NMSUtils.getNMSClassWithoutException("IRegistry");
        if (iRegistryClass != null) {
            try {
                iRegistryAttributeBaseField = iRegistryClass.getField("ATTRIBUTE");
                getiRegistryByMinecraftKeyMethod = iRegistryClass.getDeclaredMethod("get", NMSUtils.minecraftKeyClass);
            } catch (NoSuchFieldException | NoSuchMethodException ignored) {

            }
        }
    }

    public String getKey() {
        if (stringKeyPresent) {
            return readString(0);
        }
        else {
            Object attributeBase = readObject(0, attributeBaseClass);
            WrappedPacket attributeBaseWrapper = new WrappedPacket(new NMSPacket(attributeBase));
            return attributeBaseWrapper.readString(0);
        }
    }

    public void setKey(String identifier) {
        if (stringKeyPresent) {
            writeString(0, identifier);
        }
        else {
            Object minecraftKey = NMSUtils.generateMinecraftKey(identifier);
            Object attributeObj = null;
            try {
                attributeObj = iRegistryAttributeBaseField.get(null);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            Object nmsAttributeBase = null;
            try {
                nmsAttributeBase = getiRegistryByMinecraftKeyMethod.invoke(attributeObj, minecraftKey);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            write(attributeBaseClass, 0, nmsAttributeBase);
        }
    }

    public double getValue() {
        return readDouble(0);
    }

    public void setValue(double value) {
        writeDouble(0, value);
    }

    public Collection<AttributeModifierWrapper> getModifiers() {
        Collection<?> collection = readObject(0, Collection.class);
        Collection<AttributeModifierWrapper> modifierWrappers = new ArrayList<>(collection.size());
        for (Object obj : collection) {
            modifierWrappers.add(new AttributeModifierWrapper(new NMSPacket(obj)));
        }
        return modifierWrappers;
    }

    public void setModifiers(Collection<AttributeModifierWrapper> attributeModifiers) {
        Collection<Object> collection = new ArrayList<>(attributeModifiers.size());
        for (AttributeModifierWrapper modifierWrapper : attributeModifiers) {
            collection.add(modifierWrapper.getNMSPacket().getRawNMSPacket());
        }
        writeObject(0, collection);
    }

    public NMSPacket getNMSPacket() {
        return packet;
    }

    public static AttributeSnapshotWrapper create(String key, double value, Collection<AttributeModifierWrapper> modifiers) {
        Object nmsAttributeSnapshot = null;
        if (attributeSnapshotClass == null) {
            attributeSnapshotClass = SubclassUtil.getSubClass(PacketTypeClasses.Play.Server.UPDATE_ATTRIBUTES, "AttributeSnapshot");
        }
        if (attributeSnapshotConstructor == null) {
            try {
                attributeSnapshotConstructor = attributeSnapshotClass.getConstructor(PacketTypeClasses.Play.Server.UPDATE_ATTRIBUTES, String.class, double.class, Collection.class);
                constructorMode = 0;
            } catch (NoSuchMethodException e) {
                try {
                    attributeSnapshotConstructor = attributeSnapshotClass.getConstructor(String.class, double.class, Collection.class);
                    constructorMode = 1;
                } catch (NoSuchMethodException e2) {
                    constructorMode = 2;
                    if (attributeBaseClass == null) {
                        attributeBaseClass = NMSUtils.getNMSClassWithoutException("AttributeBase");
                    }
                    if (attributeSnapshotConstructor == null) {
                        try {
                            attributeSnapshotConstructor = attributeSnapshotClass.getConstructor(attributeBaseClass, double.class, Collection.class);
                        } catch (NoSuchMethodException e3) {
                            e3.printStackTrace();
                        }
                    }

                    Class<?> iRegistryClass = NMSUtils.getNMSClassWithoutException("IRegistry");
                    if (iRegistryClass != null) {
                        try {
                            iRegistryAttributeBaseField = iRegistryClass.getField("ATTRIBUTE");
                            getiRegistryByMinecraftKeyMethod = iRegistryClass.getDeclaredMethod("get", NMSUtils.minecraftKeyClass);
                        } catch (NoSuchFieldException | NoSuchMethodException ignored) {

                        }
                    }
                }
            }
        }

        List<Object> nmsModifiers = new ArrayList<>(modifiers.size());
        for (AttributeModifierWrapper modifier : modifiers) {
            nmsModifiers.add(modifier.getNMSPacket().getRawNMSPacket());
        }
        try {
            switch (constructorMode) {
                case 0:
                    nmsAttributeSnapshot = attributeSnapshotConstructor.newInstance(null, key, value, nmsModifiers);
                    break;
                case 1:
                    nmsAttributeSnapshot = attributeSnapshotConstructor.newInstance(key, value, nmsModifiers);
                    break;
                case 2:
                    Object minecraftKey = NMSUtils.generateMinecraftKey(key);
                    Object attributeObj = iRegistryAttributeBaseField.get(null);
                    Object nmsAttributeBase = getiRegistryByMinecraftKeyMethod.invoke(attributeObj, minecraftKey);
                    nmsAttributeSnapshot = attributeSnapshotConstructor.newInstance(nmsAttributeBase, value, nmsModifiers);
                    break;
                default:
                    return null;
            }
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return new AttributeSnapshotWrapper(new NMSPacket(nmsAttributeSnapshot));
    }
}
