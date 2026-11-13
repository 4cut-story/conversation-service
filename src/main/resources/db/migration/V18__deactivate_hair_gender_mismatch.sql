-- 참조 그림의 머리 모양이 성별과 반대로 보이는 일상 캐릭터를 끈다(지우지 않는다).
-- 일상 참조 그림은 성별과 상관없이 머리 모양을 골라 그려서, 긴 머리 남자는 여자처럼, 짧은 머리 여자는 남자처럼 보였다.
-- 그림 모델은 참조 그림을 따르고 목소리는 콘티 성별을 따라서, 남자 안내원이 여자로 그려지고 남자 목소리가 나왔다.
-- 참조 그림 1,013장의 목 옆 머리카락을 재서 고른 남자 58명(모습 문구에 긴 머리가 없어도 모자 아래 긴 머리가 그려진 경우 포함)과
-- 모습 문구가 짧은 머리인 여자 52명이다. 다시 쓰려면 active를 TRUE로 되돌린다.
UPDATE characters SET active = FALSE, updated_at = CURRENT_TIMESTAMP(6)
WHERE public_id IN (
    -- 긴 머리로 그려진 남자
    'W7TZqTgr5YCWBLpj-33ph', -- 가솔: middle-aged man, straw hat, green overalls
    'CqwJ6O7oCG2ERxFgpZPv3', -- 가율: little boy, space helmet, space suit
    'PqH-d5w7AK_M4fdS8E0kq', -- 강마훈: young man, long black hair, olive overalls
    'tXAvuzMcJa0YLgWwFnUTd', -- 고바현: young man, long blonde hair, sky blue jacket
    'Kdzl_Bn_PNc6AW2mUVllg', -- 고준은: young man, chef hat, white chef jacket
    'J6-EpLHTPGPUOO6e90cpv', -- 노소수: young man, long dark brown hair, olive flannel shirt
    'whIiUex_ZcA9Nx0blbXzW', -- 노카음: middle-aged man, long black hair, yellow shop apron
    'mRgcpw2RwCoRvmUpGfhfJ', -- 다원: little boy, beret, painter apron
    'X2a2qwzLHFqlm9XwBOkhn', -- 라호: young man, long dark brown hair, florist apron
    's7eLHdBo_G0f_kSu0Rkny', -- 마연: middle-aged man, long gray hair, white shirt
    'DhPn07KvccUoVDpW_0c-O', -- 민수서: young man, police cap, police uniform
    'Kf3YIXoRRb2mTtqszjK2x', -- 민훈: young man, cap, postal uniform
    'QvzZgb4p2SMfR7irCORHq', -- 바나: middle-aged man, long gray hair, navy sports jersey
    'VfgZTlHIQ4d-NS38i7c6G', -- 방수람: young man, fedora hat, trench coat
    'SSEZ0C-QvRct67lF-OiFS', -- 배소율: middle-aged man, long black hair, white doctor coat
    'gNKPT8CV6tZbyCJge7yUO', -- 보수: middle-aged man, long auburn hair, brown shirt
    'lTUy8peEgRvxZ9d_DuYyP', -- 보윤: young man, long black hair, sky blue raincoat
    'FhP9MvQIdWF6DbmqiB55D', -- 서연: little boy, long auburn hair, red raincoat
    'fms90ZSU56rBX7nhNdD9H', -- 서음: middle-aged man, long dark brown hair, navy flannel shirt
    'K9IYPvIqvTRSvNDJukvy5', -- 서파담: young man, fedora hat, trench coat
    'mNClHlA-ShH7cIakWhBnC', -- 성서람: little boy, long dark brown hair, orange striped shirt
    'ZHvSCHTkUwnffOgaHtZoQ', -- 소은: middle-aged man, police cap, police uniform
    'UKMk05uwf1AX5u6eSKCZE', -- 송서음: young man, long black hair, purple suit
    'Z6jLOtTI88LWAH4KWRVaz', -- 송차훈: young man, long auburn hair, gray hoodie
    'ngcsCXq3K_KkV3AiYjhLh', -- 수율: middle-aged man, white headband, white doctor coat
    '_O6wuPDCD_f_gS4XNl0-j', -- 시서: young man, space helmet, space suit
    'I7SB92YCG_SgnnHhYBeN6', -- 심주원: little boy, long brown hair, pink hoodie
    'pexEhajxBz6SkkL3EQOTj', -- 아나: young man, long black hair, orange overalls
    'BIBybG1bAbUs305LkmKjE', -- 양민람: middle-aged man, long blonde hair, brown shirt
    'HDrXwyQvDVATBPB-EGalT', -- 연아: young man, fedora hat, white shirt
    'kGK3zmWk-gUUpm0XIBWWO', -- 예윤: young man, chef hat, white chef jacket
    '8zrgKR1Hfd81-Hf_FVKWn', -- 우나: young man, long blonde hair, orange shirt
    'aAIWQkTw5Ifs-gONmXidG', -- 우람: young man, long brown hair, white doctor coat
    'EM9-21TThKdZrNKmxZgiW', -- 우영: middle-aged man, white headband, white doctor coat
    'OuOFI2-uCbrl4m8X0JN4o', -- 윤아: young man, fedora hat, crimson shirt
    'BdvwxlwOvCWkHSehuYKGm', -- 윤희: young man, long black hair, white doctor coat
    'TuwvpywdnfvBAJLmwMyXy', -- 은별: young man, beret, painter apron
    'vn7ogxag4NIV44vayl_ks', -- 은음: young man, long black hair, green jacket
    'NQJ7SbOh77eVqaGhmrJfk', -- 임현결: young man, chef hat, white chef jacket
    'ZOdciAUofI8QXe-5TMALR', -- 재담: young man, long black hair, indigo sports jersey
    '77MgYzJIfRguZTfoeRGg8', -- 재우: little boy, long auburn hair, gray hoodie
    'i4kWSPetP9y7WBmrcA7Js', -- 정보서: middle-aged man, long blonde hair, blue overalls
    'GP--xIzS9_g0B7EeiBas5', -- 주희: young man, white headband, white doctor coat
    'IU8MP831FKXOdmDalYTuC', -- 준람: middle-aged man, long black hair, purple shop apron
    'J2dgHVe8Xp8keYPUPHbSV', -- 지카린: middle-aged man, beret, painter apron
    '17yAlRaHz-cTz3C8ZCZI1', -- 진은: young man, long black hair, barista apron
    '4rMMS12euk4pE5Jxpt6HL', -- 진희: young man, long dark brown hair, purple striped shirt
    'z5-jPb4teNnaSGE0-2hLA', -- 최은진: middle-aged man, long dark brown hair, red raincoat
    '56O2Xe710mspdmGuwHUkr', -- 카훈: young man, work cap, work coverall
    'QE0ceZc_JOiqt5RZ8Ff7Q', -- 타나: middle-aged man, long black hair, white doctor coat
    'gsf3mxZilK66GyL8BLj8Z', -- 타아: middle-aged man, fedora hat, trench coat
    '_4Y8czJwELtNt8CdAg4SM', -- 태린: young man, long blonde hair, indigo sweater
    '5aIMO0tSKvoMDYp0xk90u', -- 하사서: young man, long brown hair, florist apron
    'KZFQwTqKEEZNbWasqjr49', -- 하카진: middle-aged man, cap, postal uniform
    'I-v4sbHsBBx3tnIyVb7lI', -- 한시별: young man, cap, postal uniform
    'k9xd5XjFH2wO6prX2PfIG', -- 허소수: young man, fire helmet, firefighter coat
    'ZAd-xyRkQ-W_VpSlnfKld', -- 현예윤: little boy, long brown hair, yellow hoodie
    'L3ETYZftzqob6eBA609GP', -- 홍도린: young man, long black hair, olive shirt
    -- 짧은 머리 여자
    'Pb4yNwD90BWwmio1Sc2CS', -- 가빈: little girl, short black hair, jade hoodie
    'TQDT_gc4vQndq9f4EG3-5', -- 고우민: young woman, short black hair, green shop apron
    'bC8LM8gCK5GBsJaS5hXki', -- 공유원: old woman, short white hair, brown hoodie
    'lZkLT4cj_EdYW2fV3nH2s', -- 나율: middle-aged woman, short blonde hair, white doctor coat
    'kopF3vQUAzjBIFh-t2WAU', -- 노유나: young woman, short dark brown hair, cream striped shirt
    '15fSYULXxz4ueC_2PRw2C', -- 마나: old woman, short gray hair, olive dress
    '9E3jKOIPwt3lEVtA_WKf3', -- 마수: little girl, short blonde hair, navy hoodie
    'cjI2HCRO5PWCCMBY2J1Y3', -- 민준: young woman, short dark brown hair, cream dress
    'S-8pgGCF8leDJVOwhuaPK', -- 바희: little girl, short dark brown hair, crimson flannel shirt
    'FvAh-dRPiiy7UHeyMwyZB', -- 배사희: little girl, short auburn hair, green sweater
    'CZyOrafmta4LLmKMKuKBw', -- 배우윤: young woman, short dark brown hair, jade cardigan
    'FXZggMwVeR5Mxlm2Hderp', -- 백윤은: young woman, short black hair, navy sports jersey
    'RSCNtI-6zlMgA9uvbbbIS', -- 사별: old woman, short gray hair, teal dress
    'ElmLWQw6FNaEcSJAR1fNo', -- 서현담: young woman, short dark brown hair, jade flannel shirt
    '7C0kXQ1_FiIjYFvpBgq76', -- 소결: young woman, short black hair, cream shirt
    'wb726JwiYW5JYwBJkR6p8', -- 손다나: young woman, short dark brown hair, cream raincoat
    'iAA8gnGqDPfxybn6gJajG', -- 수람: middle-aged woman, short blonde hair, jade sports jersey
    'TGj2GYR5xgL8xelfDzWp3', -- 아호: young woman, short auburn hair, yellow cardigan
    'pkYSe_qadAKinLzjzSo38', -- 안수진: young woman, short brown hair, white doctor coat
    'mnE_bLXg3_lTyfb6IeMoW', -- 양마준: young woman, short black hair, teal striped shirt
    '8D30huqpKNkWNzn2sTSub', -- 양카빈: old woman, short gray hair, orange hoodie
    'dTRnDq_MJ64vQ3X7Cb7kr', -- 엄타은: young woman, short brown hair, purple cardigan
    'ibYIFnr4vD_ulZjUUgYTK', -- 연라: young woman, short black hair, barista apron
    'WB-GJkn26GPKpkk284uFK', -- 연연: young woman, short dark brown hair, green cardigan
    '7xIDF704NmQ0GjTRJ3-bD', -- 우율: old woman, short white hair, barista apron
    'g7644WziBrmRKWzxvUI0h', -- 원준민: young woman, short brown hair, navy sports jersey
    'BKNSwhOw_rmK0Q-iy51Ie', -- 윤결: old woman, short gray hair, indigo cardigan
    '8VplqipoO4ICKvZXOf7Ws', -- 윤시우: young woman, short dark brown hair, brown suit
    'PHad9TYxhmdEbVYclW9-F', -- 자결: young woman, short black hair, barista apron
    'l0UIKd20MhVqV3q00eNPo', -- 장가나: young woman, short black hair, brown jacket
    'EOyVq8jIyHLL_qi02ehgy', -- 전은아: young woman, short blonde hair, white raincoat
    'St1emK7gc8p0I1CgRmQAE', -- 정채람: little girl, short dark brown hair, yellow hoodie
    '66ES_l-YiUOSmSHtwJ2uW', -- 조아준: old woman, short white hair, teal suit
    '2x1U851n4kiVWvp1ezV1Z', -- 주나율: old woman, short gray hair, barista apron
    'P-q3Jq_iSQuKfypKLYyG3', -- 주빈: old woman, short gray hair, yellow suit
    '05W_DG8SIEF29M9Eq5V8l', -- 준담: old woman, short white hair, green jacket
    'whRvbqHAl0Jl37ZcyrXXh', -- 지아: middle-aged woman, short black hair, olive hoodie
    'lhf9_-siIcdNqsfnLj225', -- 지영: old woman, short gray hair, crimson blouse
    'WTfPz5FpSXVxhxqPJ5S5k', -- 진서: old woman, short white hair, yellow jacket
    'ASxNA197KJsr9ZPEF4zgp', -- 진예영: middle-aged woman, short auburn hair, sky blue jacket
    'XYAkKLeF2ySYCfq2lD_at', -- 차서: young woman, short dark brown hair, barista apron
    'TdlWc1_H53dAR4Q-eS8yv', -- 차유수: old woman, short white hair, red cardigan
    '7RzoVRDqcyXaa7MxjvQ0R', -- 차윤: young woman, short black hair, barista apron
    'EunwSSDgkM9ZEe-ePPXdN', -- 채별: old woman, short white hair, green dress
    'fDMBQ3I-AufTKLZVv1EVz', -- 카솔: middle-aged woman, short brown hair, green cardigan
    '0EjyeOqLkn3Bvrz5eaYJ2', -- 타서: old woman, short gray hair, barista apron
    'PY8RxTGVPrJE_j2zX0e04', -- 파별: young woman, short black hair, brown overalls
    'izT2a4SQDwyodlpPZmAO4', -- 하나담: little girl, short auburn hair, indigo striped shirt
    'azKZ-g0Ivy2EP6IZ3P_w3', -- 하라: old woman, short gray hair, white jacket
    'qx6TZzabxqcDQNhc5v_dK', -- 하아: young woman, short black hair, yellow dress
    'Pz14Rib-aS7A2VNcq3hFy', -- 허준민: old woman, short white hair, barista apron
    'KTOOEoIWoSfZnIBFm9t3P' -- 홍사결: little girl, short auburn hair, yellow hoodie
);
