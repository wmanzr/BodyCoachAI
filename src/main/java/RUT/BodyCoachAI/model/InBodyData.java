package RUT.BodyCoachAI.model;

public class InBodyData {
    private Integer age;                 // возраст (лет)
    private Double height;               // рост (см)
    private String gender;               // пол (male / female)

    private Double weight;               // вес (кг)
    private Double muscleMass;           // мышечная масса (кг)
    private Double fatMass;              // масса жира (кг)

    private Double bodyFatPercentage;    // процент жира в теле (%)
    private Double bmi;                  // индекс массы тела (ИМТ)
    private Integer visceralFatLevel;    // уровень висцерального жира

    private Double bmr;                  // базовый метаболизм (ккал)

    private Double inBodyScore;          // итоговая оценка InBody (например: "Хорошо", "Норма", "Ниже нормы")

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Double getMuscleMass() {
        return muscleMass;
    }

    public void setMuscleMass(Double muscleMass) {
        this.muscleMass = muscleMass;
    }

    public Double getFatMass() {
        return fatMass;
    }

    public void setFatMass(Double fatMass) {
        this.fatMass = fatMass;
    }

    public Double getBodyFatPercentage() {
        return bodyFatPercentage;
    }

    public void setBodyFatPercentage(Double bodyFatPercentage) {
        this.bodyFatPercentage = bodyFatPercentage;
    }

    public Double getBmi() {
        return bmi;
    }

    public void setBmi(Double bmi) {
        this.bmi = bmi;
    }

    public Integer getVisceralFatLevel() {
        return visceralFatLevel;
    }

    public void setVisceralFatLevel(Integer visceralFatLevel) {
        this.visceralFatLevel = visceralFatLevel;
    }

    public Double getBmr() {
        return bmr;
    }

    public void setBmr(Double bmr) {
        this.bmr = bmr;
    }

    public Double getInBodyScore() {
        return inBodyScore;
    }

    public void setInBodyScore(Double inBodyScore) {
        this.inBodyScore = inBodyScore;
    }

    public String toPromptString() {
        return String.format(
                "Данные InBody:\n" +
                        "Возраст: %s\n" +
                        "Рост: %s см\n" +
                        "Пол: %s\n" +
                        "Вес: %s кг\n" +
                        "Мышечная масса: %s кг\n" +
                        "Масса жира: %s кг\n" +
                        "Процент жира: %s %%\n" +
                        "ИМТ: %s\n" +
                        "Висцеральный жир: %s\n" +
                        "BMR: %s ккал\n" +
                        "Итоговая оценка InBody: %s/100",
                age, height, gender, weight, muscleMass, fatMass,
                bodyFatPercentage, bmi, visceralFatLevel, bmr, inBodyScore
        );
    }
}