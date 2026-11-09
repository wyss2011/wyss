package com.nonid;

public interface GenderHolder {
   int GENDER_NONE = 0;
   int GENDER_MALE = 1;
   int GENDER_FEMALE = 2;
   int GENDER_BOTH = 3;

   int getGenderMask();

   void setGenderMask(int var1);

   default boolean hasGender(int mask) {
      return (this.getGenderMask() & mask) != 0;
   }
}
